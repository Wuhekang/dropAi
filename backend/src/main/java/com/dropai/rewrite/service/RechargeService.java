package com.dropai.rewrite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.dto.RechargeAuditDTO;
import com.dropai.rewrite.dto.RechargeConfirmDTO;
import com.dropai.rewrite.dto.RechargeOrderCreateDTO;
import com.dropai.rewrite.entity.PointTransaction;
import com.dropai.rewrite.entity.RechargeOrder;
import com.dropai.rewrite.entity.UserAccount;
import com.dropai.rewrite.entity.UserPointsLog;
import com.dropai.rewrite.mapper.PointTransactionMapper;
import com.dropai.rewrite.mapper.RechargeOrderMapper;
import com.dropai.rewrite.mapper.UserAccountMapper;
import com.dropai.rewrite.mapper.SchoolMapper;
import com.dropai.rewrite.entity.School;
import com.dropai.rewrite.mapper.UserPointsLogMapper;
import com.dropai.rewrite.vo.RechargeOrderVO;
import com.dropai.rewrite.vo.RechargePlanVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class RechargeService {
    private static final Logger log = LoggerFactory.getLogger(RechargeService.class);
    private static final BigDecimal STANDARD_PRICE_PER_10 = new BigDecimal("2.00");
    private static final Map<String, Plan> PLANS = Map.of(
            "PLAN_10", new Plan("PLAN_10", 10, 50, false),
            "PLAN_20", new Plan("PLAN_20", 20, 100, true),
            "PLAN_100", new Plan("PLAN_100", 100, 500, false)
    );

    private final RechargeOrderMapper orderMapper;
    private final UserAccountMapper userMapper;
    private final SchoolMapper schoolMapper;
    private final UserPointsLogMapper pointsLogMapper;
    private final PointTransactionMapper transactionMapper;
    private final EpayService epayService;
    private final RechargeReconciliationAuditService reconciliationAudit;

    public RechargeService(RechargeOrderMapper orderMapper,
                           UserAccountMapper userMapper, SchoolMapper schoolMapper,
                           UserPointsLogMapper pointsLogMapper,
                           PointTransactionMapper transactionMapper,
                           EpayService epayService, RechargeReconciliationAuditService reconciliationAudit) {
        this.orderMapper = orderMapper;
        this.userMapper = userMapper;
        this.schoolMapper = schoolMapper;
        this.pointsLogMapper = pointsLogMapper;
        this.transactionMapper = transactionMapper;
        this.epayService = epayService;
        this.reconciliationAudit = reconciliationAudit;
    }

    public List<RechargePlanVO> plans() {
        Pricing pricing = currentPricing();
        return PLANS.values().stream()
                .sorted(Comparator.comparingInt(Plan::amount))
                .map(plan -> {
                    BigDecimal amount = BigDecimal.valueOf(plan.amount()).setScale(2);
                    return new RechargePlanVO(plan.planId(), amount,
                            calculateRechargePoints(amount, pricing.pricePer10()), plan.recommended());
                })
                .toList();
    }

    public Map<String, Object> pricing() {
        Pricing pricing = currentPricing();
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("pricePer10", pricing.pricePer10());
        out.put("minAmount", pricing.minAmount());
        out.put("maxAmount", pricing.maxAmount());
        out.put("schoolPricing", pricing.schoolPricing());
        out.put("schoolId", pricing.schoolId());
        out.put("schoolName", pricing.schoolName());
        return out;
    }

    @Transactional
    public RechargeOrderVO createOrder(RechargeOrderCreateDTO dto) {
        Long userId = AuthContext.requireUserId();
        if (dto.getUserId() != null && !dto.getUserId().equals(userId)) {
            throw new IllegalArgumentException("不能为其他用户创建充值订单");
        }
        LockedRechargeAccount locked = lockRechargeAccount(userId);
        UserAccount user = locked.user();
        Pricing pricing = pricingFor(user, locked.school());
        BigDecimal amount = pricing.schoolPricing()
                ? validateSchoolAmount(dto.getAmount(), pricing.pricePer10(), pricing.maxAmount())
                : validateAmount(dto.getAmount());
        int points = calculateRechargePoints(amount, pricing.pricePer10());
        RechargeOrder order = new RechargeOrder();
        order.setUserId(userId);
        order.setSchoolId(user.getSchoolId() == null ? 0L : user.getSchoolId());
        order.setOrderNo("R" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase());
        order.setAmount(amount);
        order.setRechargePricePer10(pricing.pricePer10());
        order.setPoints(points);
        order.setStatus("pending");
        order.setPayMethod(normalizePayMethod(dto.getPayMethod()));
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.insert(order);
        return RechargeOrderVO.of(order, epayService.createPayUrl(order));
    }

    public List<RechargeOrderVO> myOrders() {
        Long userId = AuthContext.requireUserId();
        return orderMapper.selectList(new LambdaQueryWrapper<RechargeOrder>()
                        .eq(RechargeOrder::getUserId, userId)
                        .orderByDesc(RechargeOrder::getCreatedAt)
                        .last("LIMIT 30"))
                .stream().map(RechargeOrderVO::of).toList();
    }

    public RechargeOrderVO myOrder(String orderNo) {
        RechargeOrder order = orderMapper.selectOne(new LambdaQueryWrapper<RechargeOrder>()
                .eq(RechargeOrder::getOrderNo, trim(orderNo))
                .eq(RechargeOrder::getUserId, AuthContext.requireUserId()));
        if (order == null) throw new IllegalArgumentException("充值订单不存在");
        return RechargeOrderVO.of(order);
    }

    public List<RechargeOrderVO> reviewOrders() {
        requireAdmin();
        return orderMapper.selectList(new LambdaQueryWrapper<RechargeOrder>()
                        .in(RechargeOrder::getStatus, "waiting_review", "pending", "approved", "paid", "rejected")
                        .orderByDesc(RechargeOrder::getUpdatedAt)
                        .last("LIMIT 80"))
                .stream().map(RechargeOrderVO::of).toList();
    }

    @Transactional
    public RechargeOrderVO confirmPayment(RechargeConfirmDTO dto) {
        Long userId = AuthContext.requireUserId();
        RechargeOrder order = orderMapper.selectOne(new LambdaQueryWrapper<RechargeOrder>()
                .eq(RechargeOrder::getOrderNo, trim(dto.getOrderNo()))
                .eq(RechargeOrder::getUserId, userId));
        if (order == null) throw new IllegalArgumentException("充值订单不存在");
        if (!"pending".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalStateException("订单状态不可提交支付确认");
        }
        if (dto.getPayAmount() == null || dto.getPayAmount().compareTo(order.getAmount()) != 0) {
            throw new IllegalArgumentException("支付金额与订单金额不一致");
        }
        String payAccountLast4 = normalizeLast4(dto.getPayAccountLast4());
        LocalDateTime updatedAt = LocalDateTime.now();
        int changed = orderMapper.confirmPending(order.getId(), userId, dto.getPayAmount(),
                payAccountLast4, dto.getProofImage(), updatedAt);
        if (changed != 1) {
            RechargeOrder latest = loadOrderForUpdate(order.getId());
            if (isCompleted(latest)) return RechargeOrderVO.of(latest);
            throw new IllegalStateException("订单正在处理或状态不可提交支付确认");
        }
        order.setPayAmount(dto.getPayAmount());
        order.setPayAccountLast4(payAccountLast4);
        order.setProofImage(dto.getProofImage());
        order.setStatus("waiting_review");
        order.setUpdatedAt(updatedAt);
        return RechargeOrderVO.of(order);
    }

    @Transactional
    public RechargeOrderVO audit(RechargeAuditDTO dto) {
        requireAdmin();
        String status = normalizeAuditStatus(dto.getStatus());
        RechargeOrder order = orderMapper.selectOne(new LambdaQueryWrapper<RechargeOrder>()
                .eq(RechargeOrder::getOrderNo, trim(dto.getOrderNo())));
        if (order == null) throw new IllegalArgumentException("充值订单不存在");
        if ("approved".equalsIgnoreCase(order.getStatus())) return RechargeOrderVO.of(order);
        if (!"waiting_review".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalStateException("只有待审核订单可以审核");
        }
        if (orderMapper.claimPending(order.getId()) != 1) {
            RechargeOrder latest = loadOrderForUpdate(order.getId());
            if (isCompleted(latest) || (latest != null && "rejected".equalsIgnoreCase(latest.getStatus())
                    && "rejected".equals(status))) return RechargeOrderVO.of(latest);
            throw new IllegalStateException("订单正在处理或状态不可审核");
        }
        order.setStatus(status);
        order.setAuditedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        if ("approved".equals(status)) {
            creditPoints(order);
            order.setPaidAt(LocalDateTime.now());
        }
        orderMapper.updateById(order);
        return RechargeOrderVO.of(order);
    }

    @Transactional
    public String handleNotify(Map<String, String> params, String source) {
        String orderNo = trim(params.get("out_trade_no"));
        String tradeNo = trim(params.get("trade_no"));
        String providerTradeNo = first(params.get("api_trade_no"), params.get("provider_trade_no"), params.get("transaction_id"));
        boolean signatureValid = epayService.verifyNotify(params);
        log.info("EPAY notify arrived orderNo={}, tradeNo={}, source={}, signatureValid={}", orderNo, tradeNo, source, signatureValid);
        if (!signatureValid) return "fail";
        if (!"TRADE_SUCCESS".equalsIgnoreCase(trim(params.get("trade_status")))) return "success";
        RechargeOrder order = orderMapper.selectOne(new LambdaQueryWrapper<RechargeOrder>().eq(RechargeOrder::getOrderNo, orderNo));
        if (order == null) return "fail";
        String originalStatus = order.getStatus();
        if ("paid".equalsIgnoreCase(originalStatus) || "approved".equalsIgnoreCase(originalStatus)) return "success";
        String money = trim(params.get("money"));
        if (money == null) money = trim(params.get("total_fee"));
        boolean amountMatches = money != null && parseAmount(money).compareTo(order.getAmount()) == 0;
        if (!amountMatches || !pointsMatchAccountRate(order)) {
            log.warn("EPAY notify rejected orderNo={}, amountMatches={}, originalStatus={}", orderNo, amountMatches, originalStatus);
            return "fail";
        }
        ensureTradeNumbersUnused(order.getId(), tradeNo, providerTradeNo);
        if (orderMapper.claimPending(order.getId()) != 1) {
            RechargeOrder latest = loadOrderForUpdate(order.getId());
            return isCompleted(latest) ? "success" : "fail";
        }
        Long transactionId = creditPoints(order);
        order.setStatus("paid"); order.setPayAmount(order.getAmount()); order.setThirdPartyTradeNo(tradeNo);
        order.setGatewayOrderNo(tradeNo); order.setProviderTradeNo(providerTradeNo);
        order.setPaidAt(LocalDateTime.now()); order.setCreditedAt(LocalDateTime.now()); order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        log.info("EPAY notify completed orderNo={}, tradeNo={}, originalStatus={}, result=credited, pointsTransactionId={}, response=success",
                orderNo, tradeNo, originalStatus, transactionId);
        return "success";
    }

    public String handleNotify(Map<String, String> params) { return handleNotify(params, "unknown"); }

    @Transactional
    public RechargeOrderVO reconcile(String orderNo, String reason) {
        UserAccount admin = requireAdminAccount();
        String cleanReason = trim(reason);
        if (cleanReason == null || cleanReason.length() < 3 || cleanReason.length() > 255)
            throw new IllegalArgumentException("补单原因长度必须为3-255个字符");
        RechargeOrder order = orderMapper.selectOne(new LambdaQueryWrapper<RechargeOrder>().eq(RechargeOrder::getOrderNo, trim(orderNo)));
        if (order == null) throw new IllegalArgumentException("充值订单不存在");
        try {
            EpayService.PaymentQuery payment = epayService.queryPaidOrder(order.getOrderNo());
            if (payment.money().compareTo(order.getAmount()) != 0) throw new IllegalStateException("支付平台金额与本地订单不一致");
            if (!"paid".equalsIgnoreCase(order.getStatus()) && !"approved".equalsIgnoreCase(order.getStatus())) {
                if (!pointsMatchAccountRate(order))
                    throw new IllegalStateException("本地订单积分计算不一致");
                if (orderMapper.claimPending(order.getId()) != 1) {
                    RechargeOrder latest = loadOrderForUpdate(order.getId());
                    if (isCompleted(latest)) {
                        writeReconcile(orderNo, admin.getId(), cleanReason, "already_paid", "idempotent");
                        return RechargeOrderVO.of(latest);
                    }
                    throw new IllegalStateException("订单正在处理或状态不可补单");
                }
                Long transactionId = creditPoints(order);
                ensureTradeNumbersUnused(order.getId(), payment.gatewayOrderNo(), payment.providerTradeNo());
                order.setStatus("paid"); order.setPayAmount(order.getAmount()); order.setThirdPartyTradeNo(payment.gatewayOrderNo());
                order.setGatewayOrderNo(payment.gatewayOrderNo()); order.setProviderTradeNo(payment.providerTradeNo());
                order.setPaidAt(LocalDateTime.now()); order.setCreditedAt(LocalDateTime.now()); order.setUpdatedAt(LocalDateTime.now());
                orderMapper.updateById(order);
                writeReconcile(orderNo, admin.getId(), cleanReason, "credited", "transactionId=" + transactionId);
            } else writeReconcile(orderNo, admin.getId(), cleanReason, "already_paid", "idempotent");
            return RechargeOrderVO.of(order);
        } catch (RuntimeException ex) {
            writeReconcile(orderNo, admin.getId(), cleanReason, "rejected", ex.getMessage());
            throw ex;
        }
    }

    private void writeReconcile(String orderNo, Long adminId, String reason, String result, String detail) {
        reconciliationAudit.record(orderNo, adminId, reason, result, detail);
    }

    private void ensureTradeNumbersUnused(Long orderId, String gatewayOrderNo, String providerTradeNo) {
        if (gatewayOrderNo != null && orderMapper.selectCount(new LambdaQueryWrapper<RechargeOrder>()
                .eq(RechargeOrder::getGatewayOrderNo, gatewayOrderNo).ne(RechargeOrder::getId, orderId)) > 0)
            throw new IllegalStateException("支付网关订单号已被其他订单使用");
        if (providerTradeNo != null && orderMapper.selectCount(new LambdaQueryWrapper<RechargeOrder>()
                .eq(RechargeOrder::getProviderTradeNo, providerTradeNo).ne(RechargeOrder::getId, orderId)) > 0)
            throw new IllegalStateException("上游交易号已被其他订单使用");
    }

    private String first(String... values) { for (String value : values) if (value != null && !value.isBlank()) return value.trim(); return null; }

    @Transactional
    public RechargeOrderVO mockPay(String orderNo) {
        if (!epayService.isMockEnabled()) throw new IllegalStateException("模拟支付在当前环境已禁用");
        Long userId = AuthContext.requireUserId();
        RechargeOrder order = orderMapper.selectOne(new LambdaQueryWrapper<RechargeOrder>()
                .eq(RechargeOrder::getOrderNo, orderNo)
                .eq(RechargeOrder::getUserId, userId));
        if (order == null) throw new IllegalArgumentException("\u5145\u503c\u8ba2\u5355\u4e0d\u5b58\u5728");
        if ("paid".equalsIgnoreCase(order.getStatus())) return RechargeOrderVO.of(order);
        if (!"pending".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalStateException("\u8ba2\u5355\u72b6\u6001\u4e0d\u53ef\u652f\u4ed8");
        }
        if (orderMapper.claimPending(order.getId()) != 1) {
            RechargeOrder latest = loadOrderForUpdate(order.getId());
            if (isCompleted(latest)) return RechargeOrderVO.of(latest);
            throw new IllegalStateException("订单正在处理或状态不可支付");
        }
        creditPoints(order);
        order.setStatus("paid");
        order.setPaidAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        return RechargeOrderVO.of(order);
    }

    private Long creditPoints(RechargeOrder order) {
        Long userId = order.getUserId();
        UserAccount before = userMapper.selectById(userId);
        if (before == null || before.getDeletedAt() != null || !Boolean.TRUE.equals(before.getAccountEnabled())) {
            throw new IllegalStateException("充值账号不存在或已停用");
        }
        int beforePoints = before.getPoints() == null ? 0 : before.getPoints();
        if (userMapper.addPoints(userId, order.getPoints()) != 1) {
            throw new IllegalStateException("充值积分入账失败");
        }
        UserAccount after = userMapper.selectById(userId);
        int afterPoints = after.getPoints() == null ? beforePoints + order.getPoints() : after.getPoints();

        UserPointsLog log = new UserPointsLog();
        log.setUserId(userId);
        log.setChangeAmount(order.getPoints());
        log.setBeforePoints(beforePoints);
        log.setAfterPoints(afterPoints);
        log.setReason("recharge");
        log.setCreatedAt(LocalDateTime.now());
        pointsLogMapper.insert(log);

        PointTransaction transaction = new PointTransaction();
        transaction.setUserId(userId);
        transaction.setJobId(order.getOrderNo());
        transaction.setFeatureCode("RECHARGE");
        transaction.setFeatureName("积分充值");
        transaction.setPointsChange(order.getPoints());
        transaction.setBalanceAfter(afterPoints);
        transaction.setRemark("充值 " + order.getAmount() + " 元，获得 " + order.getPoints() + " 积分");
        transaction.setCreatedAt(LocalDateTime.now());
        transactionMapper.insert(transaction);
        return transaction.getId();
    }

    public BigDecimal validateAmount(BigDecimal amount) {
        if (amount == null) throw new IllegalArgumentException("充值金额不能为空");
        BigDecimal normalized = amount.stripTrailingZeros();
        if (normalized.scale() > 0) throw new IllegalArgumentException("充值金额必须为整数");
        if (normalized.compareTo(BigDecimal.ONE) < 0) throw new IllegalArgumentException("充值金额不能少于1元");
        if (normalized.compareTo(BigDecimal.valueOf(1000)) > 0) throw new IllegalArgumentException("充值金额不能超过1000元");
        return normalized.setScale(2);
    }

    public BigDecimal validateSchoolAmount(BigDecimal amount) { return validateSchoolAmount(amount,new BigDecimal("0.30")); }
    public BigDecimal validateSchoolAmount(BigDecimal amount,BigDecimal pricePer10) {
        return validateSchoolAmount(amount, pricePer10, new BigDecimal("100000.00"));
    }

    public BigDecimal validateSchoolAmount(BigDecimal amount, BigDecimal pricePer10, BigDecimal maxAmount) {
        if (amount == null) throw new IllegalArgumentException("充值金额不能为空");
        BigDecimal normalized = amount.stripTrailingZeros();
        if (normalized.scale() > 2) throw new IllegalArgumentException("充值金额最多保留两位小数");
        if (amount.compareTo(pricePer10) < 0) throw new IllegalArgumentException("学校充值金额不能少于每10积分价格");
        if (amount.compareTo(maxAmount) > 0) throw new IllegalArgumentException("学校充值金额不能超过" + maxAmount.stripTrailingZeros().toPlainString() + "元");
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    public int calculateRechargePoints(BigDecimal amount, boolean schoolAccount) { return calculateRechargePoints(amount,schoolAccount?new BigDecimal("0.30"):null); }
    public int calculateRechargePoints(BigDecimal amount, BigDecimal pricePer10) {
        BigDecimal snapshotPrice = pricePer10 == null ? STANDARD_PRICE_PER_10 : pricePer10;
        return amount.multiply(BigDecimal.TEN).divide(snapshotPrice, 0, RoundingMode.DOWN).intValueExact();
    }

    private boolean pointsMatchAccountRate(RechargeOrder order) {
        if (order.getPoints() == null || order.getAmount() == null) return false;
        if (order.getRechargePricePer10() == null) return order.getPoints() > 0;
        return order.getPoints() == calculateRechargePoints(order.getAmount(),order.getRechargePricePer10());
    }

    private RechargeOrder loadOrderForUpdate(Long orderId) {
        if (orderId == null) return null;
        return orderMapper.selectOne(new LambdaQueryWrapper<RechargeOrder>()
                .eq(RechargeOrder::getId, orderId)
                .last("FOR UPDATE"));
    }

    private boolean isCompleted(RechargeOrder order) {
        return order != null && ("paid".equalsIgnoreCase(order.getStatus())
                || "approved".equalsIgnoreCase(order.getStatus()));
    }

    private Pricing currentPricing() {
        UserAccount user = userMapper.selectById(AuthContext.requireUserId());
        if (user == null || user.getDeletedAt() != null || !Boolean.TRUE.equals(user.getAccountEnabled()))
            throw new IllegalArgumentException("用户不存在或已停用");
        return pricingFor(user, false);
    }

    private Pricing pricingFor(UserAccount user, boolean lockSchool) {
        Long schoolId = user.getSchoolId() == null ? 0L : user.getSchoolId();
        boolean eligibleRole = "USER".equalsIgnoreCase(user.getRole()) || "SCHOOL_VIEWER".equalsIgnoreCase(user.getRole());
        if (eligibleRole && schoolId > 0) {
            School school = lockSchool
                    ? schoolMapper.selectOne(new LambdaQueryWrapper<School>()
                            .eq(School::getId, schoolId)
                            .isNull(School::getDeletedAt)
                            .last("FOR UPDATE"))
                    : schoolMapper.selectById(schoolId);
            return pricingFor(user, school);
        }
        return new Pricing(STANDARD_PRICE_PER_10, BigDecimal.ONE.setScale(2),
                new BigDecimal("1000.00"), false, 0L, null);
    }

    private Pricing pricingFor(UserAccount user, School school) {
        Long schoolId = user.getSchoolId() == null ? 0L : user.getSchoolId();
        boolean eligibleRole = "USER".equalsIgnoreCase(user.getRole()) || "SCHOOL_VIEWER".equalsIgnoreCase(user.getRole());
        if (eligibleRole && schoolId > 0) {
            if (school == null || school.getDeletedAt() != null || !Boolean.TRUE.equals(school.getEnabled()))
                throw new IllegalStateException("学校不存在或已停用");
            BigDecimal schoolPrice = school.getRechargePricePer10() == null
                    ? SchoolService.MIN_RECHARGE_PRICE : school.getRechargePricePer10();
            if (schoolPrice.compareTo(SchoolService.MIN_RECHARGE_PRICE) < 0 || schoolPrice.stripTrailingZeros().scale() > 2)
                throw new IllegalStateException("学校账户充值价格配置无效");
            schoolPrice = schoolPrice.setScale(2, RoundingMode.UNNECESSARY);
            boolean schoolViewer = "SCHOOL_VIEWER".equalsIgnoreCase(user.getRole());
            BigDecimal price = schoolPrice;
            if (!schoolViewer) {
                BigDecimal configuredMinimum = school.getStudentRechargeMinPricePer10() == null
                        ? SchoolService.DEFAULT_STUDENT_RECHARGE_MIN_PRICE
                        : school.getStudentRechargeMinPricePer10();
                if (configuredMinimum.compareTo(SchoolService.MIN_RECHARGE_PRICE) < 0
                        || configuredMinimum.compareTo(SchoolService.MAX_RECHARGE_PRICE) > 0
                        || configuredMinimum.stripTrailingZeros().scale() > 2) {
                    throw new IllegalStateException("下级账号最低限价配置无效");
                }
                BigDecimal minimum = schoolPrice.max(configuredMinimum)
                        .max(SchoolService.MIN_RECHARGE_PRICE);
                price = school.getStudentRechargePricePer10() == null
                        ? SchoolService.DEFAULT_STUDENT_RECHARGE_PRICE : school.getStudentRechargePricePer10();
                if (price.compareTo(minimum) < 0
                        || price.compareTo(SchoolService.MAX_RECHARGE_PRICE) > 0
                        || price.stripTrailingZeros().scale() > 2) {
                    throw new IllegalStateException("下级账号充值价格配置无效");
                }
            }
            price = price.setScale(2, RoundingMode.UNNECESSARY);
            BigDecimal max = schoolViewer
                    ? new BigDecimal("100000.00") : new BigDecimal("1000.00");
            return new Pricing(price.setScale(2), price.setScale(2), max, true, schoolId, school.getSchoolName());
        }
        return new Pricing(STANDARD_PRICE_PER_10, BigDecimal.ONE.setScale(2),
                new BigDecimal("1000.00"), false, 0L, null);
    }

    private LockedRechargeAccount lockRechargeAccount(Long userId) {
        UserAccount hint = userMapper.selectById(userId);
        if (hint == null || hint.getDeletedAt() != null || !Boolean.TRUE.equals(hint.getAccountEnabled())) {
            throw new IllegalArgumentException("用户不存在或已停用");
        }
        Long hintedSchoolId = normalizedSchoolId(hint.getSchoolId());
        School lockedSchool = null;
        if (hintedSchoolId > 0) {
            lockedSchool = schoolMapper.selectOne(new LambdaQueryWrapper<School>()
                    .eq(School::getId, hintedSchoolId)
                    .isNull(School::getDeletedAt)
                    .last("FOR UPDATE"));
        }
        UserAccount lockedUser = userMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getId, userId)
                .isNull(UserAccount::getDeletedAt)
                .last("FOR UPDATE"));
        if (lockedUser == null || !Boolean.TRUE.equals(lockedUser.getAccountEnabled())) {
            throw new IllegalArgumentException("用户不存在或已停用");
        }
        if (!Objects.equals(hintedSchoolId, normalizedSchoolId(lockedUser.getSchoolId()))) {
            throw new IllegalStateException("账号学校归属已变化，请重试");
        }
        return new LockedRechargeAccount(lockedUser, lockedSchool);
    }

    private Long normalizedSchoolId(Long schoolId) {
        return schoolId == null || schoolId <= 0 ? 0L : schoolId;
    }

    private String normalizeLast4(String value) {
        String trimmed = trim(value);
        if (trimmed == null || !trimmed.matches("\\d{4}")) {
            throw new IllegalArgumentException("请填写支付账号后四位");
        }
        return trimmed;
    }

    private String normalizeAuditStatus(String value) {
        String status = trim(value);
        if (status == null) throw new IllegalArgumentException("审核状态不能为空");
        status = status.toLowerCase(Locale.ROOT);
        if (!"approved".equals(status) && !"rejected".equals(status)) {
            throw new IllegalArgumentException("审核状态仅支持 approved 或 rejected");
        }
        return status;
    }

    private UserAccount requireAdminAccount() {
        UserAccount account = userMapper.selectById(AuthContext.requireUserId());
        if (account == null || account.getRole() == null || !"admin".equalsIgnoreCase(account.getRole())) {
            throw new IllegalStateException("仅管理员可操作");
        }
        return account;
    }

    private void requireAdmin() { requireAdminAccount(); }

    private String normalizePayMethod(String value) {
        if (value == null || value.isBlank()) return "alipay_personal";
        return value.trim().toLowerCase();
    }

    private BigDecimal parseAmount(String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return BigDecimal.valueOf(-1);
        }
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private record Plan(String planId, int amount, int points, boolean recommended) {
    }

    private record Pricing(BigDecimal pricePer10, BigDecimal minAmount, BigDecimal maxAmount,
                           boolean schoolPricing, Long schoolId, String schoolName) {
    }

    private record LockedRechargeAccount(UserAccount user, School school) {
    }
}
