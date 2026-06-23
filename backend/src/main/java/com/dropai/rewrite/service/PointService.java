package com.dropai.rewrite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.dto.FeaturePricingUpdateDTO;
import com.dropai.rewrite.entity.FeaturePricing;
import com.dropai.rewrite.entity.PointTransaction;
import com.dropai.rewrite.entity.UserAccount;
import com.dropai.rewrite.mapper.FeaturePricingMapper;
import com.dropai.rewrite.mapper.PointTransactionMapper;
import com.dropai.rewrite.mapper.UserAccountMapper;
import com.dropai.rewrite.vo.FeaturePricingVO;
import com.dropai.rewrite.vo.PointAccountVO;
import com.dropai.rewrite.vo.PointTransactionVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

@Service
public class PointService {
    public static final String DESIGN_GENERATE = "DESIGN_GENERATE";
    public static final String CAD_GENERATE = "CAD_GENERATE";
    public static final String MODEL_GENERATE = "MODEL_GENERATE";
    public static final String DOCX_GENERATE = "DOCX_GENERATE";
    public static final String ZIP_EXPORT = "ZIP_EXPORT";
    public static final String DOCUMENT_REWRITE = "DOCUMENT_REWRITE";
    public static final String DOCUMENT_HUMANIZE = "DOCUMENT_HUMANIZE";
    public static final String DOCUMENT_DOUBLE = "DOCUMENT_DOUBLE";

    private final UserAccountMapper userMapper;
    private final FeaturePricingMapper pricingMapper;
    private final PointTransactionMapper transactionMapper;

    public PointService(UserAccountMapper userMapper, FeaturePricingMapper pricingMapper, PointTransactionMapper transactionMapper) {
        this.userMapper = userMapper;
        this.pricingMapper = pricingMapper;
        this.transactionMapper = transactionMapper;
    }

    @Transactional
    public <T> T chargeAfterSuccess(String featureCode, String remark, Supplier<T> action) {
        Long userId = AuthContext.requireUserId();
        FeaturePricing pricing = requirePricing(featureCode);
        ensureEnough(userId, pricing);
        T result = action.get();
        deduct(userId, pricing, remark);
        return result;
    }

    public void checkPoints(String featureCode) {
        Long userId = AuthContext.requireUserId();
        ensureEnough(userId, requirePricing(featureCode));
    }

    public int currentPoints(Long userId) {
        return value(requireUser(userId).getPoints());
    }

    public void ensureEnoughCustom(Long userId, int costPoints) {
        if (costPoints <= 0) {
            return;
        }
        int balance = currentPoints(userId);
        if (balance < costPoints) {
            throw new PointsNotEnoughException("积分不足，预计需要 " + costPoints + " 积分，当前仅有 " + balance + " 积分");
        }
    }

    @Transactional
    public void deductCustom(Long userId, String jobId, String featureCode, String featureName, int costPoints, String remark) {
        if (costPoints <= 0) {
            return;
        }
        ensureEnoughCustom(userId, costPoints);
        int updated = userMapper.deductPoints(userId, costPoints);
        if (updated <= 0) {
            throw new PointsNotEnoughException("积分不足，预计需要 " + costPoints + " 积分");
        }
        UserAccount after = requireUser(userId);
        PointTransaction transaction = new PointTransaction();
        transaction.setUserId(userId);
        transaction.setJobId(jobId);
        transaction.setFeatureCode(featureCode);
        transaction.setFeatureName(featureName);
        transaction.setPointsChange(-costPoints);
        transaction.setBalanceAfter(value(after.getPoints()));
        transaction.setRemark(remark == null ? "" : remark);
        transaction.setCreatedAt(LocalDateTime.now());
        transactionMapper.insert(transaction);
    }

    public PointAccountVO myAccount() {
        Long userId = AuthContext.requireUserId();
        UserAccount user = requireUser(userId);
        List<PointTransactionVO> recent = transactionMapper.selectList(new LambdaQueryWrapper<PointTransaction>()
                        .eq(PointTransaction::getUserId, userId)
                        .orderByDesc(PointTransaction::getCreatedAt)
                        .last("LIMIT 20"))
                .stream().map(PointTransactionVO::of).toList();
        return new PointAccountVO(value(user.getPoints()), value(user.getTotalPoints()), value(user.getUsedPoints()), recent);
    }

    public List<FeaturePricingVO> listPricing() {
        requireAdmin();
        return pricingMapper.selectList(new LambdaQueryWrapper<FeaturePricing>().orderByAsc(FeaturePricing::getId))
                .stream().map(FeaturePricingVO::of).toList();
    }

    public List<PointTransactionVO> myTransactions() {
        Long userId = AuthContext.requireUserId();
        return transactionMapper.selectList(new LambdaQueryWrapper<PointTransaction>()
                        .eq(PointTransaction::getUserId, userId)
                        .orderByDesc(PointTransaction::getCreatedAt)
                        .last("LIMIT 50"))
                .stream().map(PointTransactionVO::of).toList();
    }

    @Transactional
    public FeaturePricingVO updatePricing(String featureCode, FeaturePricingUpdateDTO dto) {
        requireAdmin();
        FeaturePricing pricing = requirePricing(featureCode);
        if (dto.getCostPoints() != null) {
            if (dto.getCostPoints() < 0) throw new IllegalArgumentException("功能积分不能为负数");
            pricing.setCostPoints(dto.getCostPoints());
        }
        if (dto.getEnabled() != null) pricing.setEnabled(dto.getEnabled());
        pricingMapper.updateById(pricing);
        return FeaturePricingVO.of(pricing);
    }

    @Transactional
    public void initializeUserPoints(UserAccount account) {
        if (account.getPoints() == null) account.setPoints(0);
        if (account.getTotalPoints() == null) account.setTotalPoints(account.getPoints());
        if (account.getUsedPoints() == null) account.setUsedPoints(0);
    }

    private void ensureEnough(Long userId, FeaturePricing pricing) {
        UserAccount user = requireUser(userId);
        int balance = value(user.getPoints());
        int cost = value(pricing.getCostPoints());
        if (!Boolean.TRUE.equals(pricing.getEnabled())) {
            throw new IllegalStateException("该功能暂未启用");
        }
        if (balance < cost) {
            throw new PointsNotEnoughException("积分不足");
        }
    }

    private void deduct(Long userId, FeaturePricing pricing, String remark) {
        int cost = value(pricing.getCostPoints());
        if (cost <= 0) return;
        int updated = userMapper.deductPoints(userId, cost);
        if (updated <= 0) throw new PointsNotEnoughException("积分不足");
        UserAccount after = requireUser(userId);
        PointTransaction transaction = new PointTransaction();
        transaction.setUserId(userId);
        transaction.setJobId(null);
        transaction.setFeatureCode(pricing.getFeatureCode());
        transaction.setFeatureName(pricing.getFeatureName());
        transaction.setPointsChange(-cost);
        transaction.setBalanceAfter(value(after.getPoints()));
        transaction.setRemark(remark == null ? "" : remark);
        transaction.setCreatedAt(LocalDateTime.now());
        transactionMapper.insert(transaction);
    }

    private FeaturePricing requirePricing(String featureCode) {
        FeaturePricing pricing = pricingMapper.selectOne(new LambdaQueryWrapper<FeaturePricing>()
                .eq(FeaturePricing::getFeatureCode, featureCode));
        if (pricing == null) throw new IllegalStateException("功能价格未配置：" + featureCode);
        return pricing;
    }

    private UserAccount requireUser(Long userId) {
        UserAccount user = userMapper.selectById(userId);
        if (user == null) throw new IllegalStateException("用户不存在");
        if (user.getPoints() == null || user.getTotalPoints() == null || user.getUsedPoints() == null) {
            user.setPoints(user.getPoints() == null ? 0 : user.getPoints());
            user.setTotalPoints(user.getTotalPoints() == null ? user.getPoints() : user.getTotalPoints());
            user.setUsedPoints(user.getUsedPoints() == null ? 0 : user.getUsedPoints());
            userMapper.updatePointSnapshot(userId, value(user.getPoints()), value(user.getTotalPoints()), value(user.getUsedPoints()));
        }
        return user;
    }

    private void requireAdmin() {
        UserAccount user = requireUser(AuthContext.requireUserId());
        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new IllegalStateException("无管理员权限");
        }
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }
}
