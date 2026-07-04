package com.dropai.rewrite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dropai.rewrite.auth.AuthContext;
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
import java.util.Map;
import java.util.UUID;

@Service
public class RechargeService {
    private static final Map<Integer, Integer> PLANS = Map.of(
            10, 100,
            30, 350,
            50, 600,
            100, 1300
    );

    private final RechargeOrderMapper orderMapper;
    private final UserAccountMapper userMapper;
    private final UserPointsLogMapper pointsLogMapper;
    private final PointTransactionMapper transactionMapper;

    public RechargeService(RechargeOrderMapper orderMapper,
                           UserAccountMapper userMapper,
                           UserPointsLogMapper pointsLogMapper,
                           PointTransactionMapper transactionMapper) {
        this.orderMapper = orderMapper;
        this.userMapper = userMapper;
        this.pointsLogMapper = pointsLogMapper;
        this.transactionMapper = transactionMapper;
    }

    public List<RechargePlanVO> plans() {
        return List.of(
                new RechargePlanVO(BigDecimal.valueOf(10), 100, false),
                new RechargePlanVO(BigDecimal.valueOf(30), 350, true),
                new RechargePlanVO(BigDecimal.valueOf(50), 600, false),
                new RechargePlanVO(BigDecimal.valueOf(100), 1300, false)
        );
    }

    @Transactional
    public RechargeOrderVO createOrder(RechargeOrderCreateDTO dto) {
        Long userId = AuthContext.requireUserId();
        int amount = dto.getAmount() == null ? 0 : dto.getAmount().intValue();
        Integer points = PLANS.get(amount);
        if (points == null) {
            throw new IllegalArgumentException("\u4e0d\u652f\u6301\u7684\u5145\u503c\u5957\u9910");
        }
        RechargeOrder order = new RechargeOrder();
        order.setUserId(userId);
        order.setOrderNo("RC" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        order.setAmount(BigDecimal.valueOf(amount));
        order.setPoints(points);
        order.setStatus("pending");
        order.setPayMethod(normalizePayMethod(dto.getPayMethod()));
        order.setCreatedAt(LocalDateTime.now());
        orderMapper.insert(order);
        return RechargeOrderVO.of(order);
    }

    public List<RechargeOrderVO> myOrders() {
        Long userId = AuthContext.requireUserId();
        return orderMapper.selectList(new LambdaQueryWrapper<RechargeOrder>()
                        .eq(RechargeOrder::getUserId, userId)
                        .orderByDesc(RechargeOrder::getCreatedAt)
                        .last("LIMIT 30"))
                .stream().map(RechargeOrderVO::of).toList();
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

        UserAccount before = userMapper.selectById(userId);
        int beforePoints = before.getPoints() == null ? 0 : before.getPoints();
        userMapper.addPoints(userId, order.getPoints());
        UserAccount after = userMapper.selectById(userId);
        int afterPoints = after.getPoints() == null ? beforePoints + order.getPoints() : after.getPoints();

        order.setStatus("paid");
        order.setPaidAt(LocalDateTime.now());
        orderMapper.updateById(order);

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
        transaction.setFeatureName("\u79ef\u5206\u5145\u503c");
        transaction.setPointsChange(order.getPoints());
        transaction.setBalanceAfter(afterPoints);
        transaction.setRemark("\u5145\u503c " + order.getAmount() + " \u5143\uff0c\u83b7\u5f97 " + order.getPoints() + " \u79ef\u5206");
        transaction.setCreatedAt(LocalDateTime.now());
        transactionMapper.insert(transaction);
        return RechargeOrderVO.of(order);
    }

    private String normalizePayMethod(String value) {
        if (value == null || value.isBlank()) return "alipay_mock";
        return value.trim().toLowerCase();
    }
}
