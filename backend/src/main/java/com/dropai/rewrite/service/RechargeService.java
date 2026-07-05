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
import com.dropai.rewrite.mapper.UserPointsLogMapper;
import com.dropai.rewrite.vo.RechargeOrderVO;
import com.dropai.rewrite.vo.RechargePlanVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class RechargeService {
    private static final Map<String, Plan> PLANS = Map.of(
            "PLAN_10", new Plan("PLAN_10", 10, 100, false),
            "PLAN_30", new Plan("PLAN_30", 30, 350, true),
            "PLAN_50", new Plan("PLAN_50", 50, 600, false),
            "PLAN_100", new Plan("PLAN_100", 100, 1300, false)
    );

    private final RechargeOrderMapper orderMapper;
    private final UserAccountMapper userMapper;
    private final UserPointsLogMapper pointsLogMapper;
    private final PointTransactionMapper transactionMapper;
    private final EpayService epayService;

    public RechargeService(RechargeOrderMapper orderMapper,
                           UserAccountMapper userMapper,
                           UserPointsLogMapper pointsLogMapper,
                           PointTransactionMapper transactionMapper,
                           EpayService epayService) {
        this.orderMapper = orderMapper;
        this.userMapper = userMapper;
        this.pointsLogMapper = pointsLogMapper;
        this.transactionMapper = transactionMapper;
        this.epayService = epayService;
    }

    public List<RechargePlanVO> plans() {
        return PLANS.values().stream()
                .sorted(Comparator.comparingInt(Plan::amount))
                .map(plan -> new RechargePlanVO(plan.planId(), BigDecimal.valueOf(plan.amount()), plan.points(), plan.recommended()))
                .toList();
    }

    @Transactional
    public RechargeOrderVO createOrder(RechargeOrderCreateDTO dto) {
        Long userId = AuthContext.requireUserId();
        if (dto.getUserId() != null && !dto.getUserId().equals(userId)) {
            throw new IllegalArgumentException("不能为其他用户创建充值订单");
        }
        Plan plan = resolvePlan(dto);
        RechargeOrder order = new RechargeOrder();
        order.setUserId(userId);
        order.setOrderNo("R" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase());
        order.setAmount(BigDecimal.valueOf(plan.amount()));
        order.setPoints(plan.points());
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
        order.setPayAmount(dto.getPayAmount());
        order.setPayAccountLast4(normalizeLast4(dto.getPayAccountLast4()));
        order.setProofImage(dto.getProofImage());
        order.setStatus("waiting_review");
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);
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
    public String handleNotify(Map<String, String> params) {
        if (!epayService.verifyNotify(params)) {
            return "fail";
        }
        String orderNo = trim(params.get("out_trade_no"));
        String tradeStatus = trim(params.get("trade_status"));
        if (!"TRADE_SUCCESS".equalsIgnoreCase(tradeStatus)) {
            return "success";
        }
        RechargeOrder order = orderMapper.selectOne(new LambdaQueryWrapper<RechargeOrder>()
                .eq(RechargeOrder::getOrderNo, orderNo));
        if (order == null) {
            return "fail";
        }
        if ("paid".equalsIgnoreCase(order.getStatus()) || "approved".equalsIgnoreCase(order.getStatus())) {
            return "success";
        }
        String money = trim(params.get("money"));
        if (money == null) money = trim(params.get("total_fee"));
        if (money == null || parseAmount(money).compareTo(order.getAmount()) != 0) {
            return "fail";
        }
        creditPoints(order);
        order.setStatus("paid");
        order.setPayAmount(order.getAmount());
        order.setPaidAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        return "success";
    }

    @Transactional
    public RechargeOrderVO mockPay(String orderNo) {
        Long userId = AuthContext.requireUserId();
        RechargeOrder order = orderMapper.selectOne(new LambdaQueryWrapper<RechargeOrder>()
                .eq(RechargeOrder::getOrderNo, orderNo)
                .eq(RechargeOrder::getUserId, userId));
        if (order == null) throw new IllegalArgumentException("\u5145\u503c\u8ba2\u5355\u4e0d\u5b58\u5728");
        if ("paid".equalsIgnoreCase(order.getStatus())) return RechargeOrderVO.of(order);
        if (!"pending".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalStateException("\u8ba2\u5355\u72b6\u6001\u4e0d\u53ef\u652f\u4ed8");
        }

        creditPoints(order);
        order.setStatus("paid");
        order.setPaidAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        return RechargeOrderVO.of(order);
    }

    private void creditPoints(RechargeOrder order) {
        Long userId = order.getUserId();
        UserAccount before = userMapper.selectById(userId);
        int beforePoints = before.getPoints() == null ? 0 : before.getPoints();
        userMapper.addPoints(userId, order.getPoints());
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
    }

    private Plan resolvePlan(RechargeOrderCreateDTO dto) {
        if (dto.getPlanId() != null && !dto.getPlanId().isBlank()) {
            Plan plan = PLANS.get(dto.getPlanId().trim().toUpperCase(Locale.ROOT));
            if (plan != null) return plan;
        }
        int amount = dto.getAmount() == null ? 0 : dto.getAmount().intValue();
        return PLANS.values().stream()
                .filter(plan -> plan.amount() == amount)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的充值套餐"));
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

    private void requireAdmin() {
        UserAccount account = userMapper.selectById(AuthContext.requireUserId());
        if (account == null || account.getRole() == null || !"admin".equalsIgnoreCase(account.getRole())) {
            throw new IllegalStateException("仅管理员可操作");
        }
    }

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
}
