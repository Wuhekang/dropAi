package com.dropai.rewrite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.dto.FeaturePricingUpdateDTO;
import com.dropai.rewrite.entity.FeaturePricing;
import com.dropai.rewrite.entity.PointTransaction;
import com.dropai.rewrite.entity.UserAccount;
import com.dropai.rewrite.entity.UserPointsLog;
import com.dropai.rewrite.mapper.FeaturePricingMapper;
import com.dropai.rewrite.mapper.PointTransactionMapper;
import com.dropai.rewrite.mapper.UserAccountMapper;
import com.dropai.rewrite.mapper.UserPointsLogMapper;
import com.dropai.rewrite.vo.FeaturePricingVO;
import com.dropai.rewrite.vo.PointAccountVO;
import com.dropai.rewrite.vo.PointTransactionVO;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final UserPointsLogMapper pointsLogMapper;

    public PointService(UserAccountMapper userMapper,
                        FeaturePricingMapper pricingMapper,
                        PointTransactionMapper transactionMapper) {
        this(userMapper, pricingMapper, transactionMapper, null);
    }

    @Autowired
    public PointService(UserAccountMapper userMapper,
                        FeaturePricingMapper pricingMapper,
                        PointTransactionMapper transactionMapper,
                        UserPointsLogMapper pointsLogMapper) {
        this.userMapper = userMapper;
        this.pricingMapper = pricingMapper;
        this.transactionMapper = transactionMapper;
        this.pointsLogMapper = pointsLogMapper;
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

    @Transactional
    public <T> T chargeCustomAfterSuccess(String featureCode, int costPoints, String remark, Supplier<T> action) {
        Long userId = AuthContext.requireUserId();
        FeaturePricing pricing = requirePricing(featureCode);
        ensureFeatureEnabled(pricing);
        ensureEnoughCustom(userId, costPoints);
        T result = action.get();
        deductCustom(userId, null, featureCode, pricing.getFeatureName(), costPoints, remark);
        return result;
    }

    public int usageCostPoints(String featureCode, int charCount) {
        FeaturePricing pricing = requirePricing(featureCode);
        ensureFeatureEnabled(pricing);
        if (charCount <= 0) {
            return 0;
        }
        int unitCost = value(pricing.getCostPoints());
        if (unitCost <= 0) {
            return 0;
        }
        return ((charCount + 999) / 1000) * unitCost;
    }

    public void checkPoints(String featureCode) {
        Long userId = AuthContext.requireUserId();
        ensureEnough(userId, requirePricing(featureCode));
    }

    public int featureCostPoints(String featureCode) {
        FeaturePricing pricing = requirePricing(featureCode);
        ensureFeatureEnabled(pricing);
        return value(pricing.getCostPoints());
    }

    @Transactional
    public void deductFeatureForJob(Long userId, String jobId, String featureCode, String remark) {
        FeaturePricing pricing = requirePricing(featureCode);
        ensureFeatureEnabled(pricing);
        deductCustom(userId, jobId, pricing.getFeatureCode(), pricing.getFeatureName(), value(pricing.getCostPoints()), remark);
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
            throw new PointsNotEnoughException(balance, costPoints);
        }
    }

    @Transactional
    public void deductCustom(Long userId, String jobId, String featureCode, String featureName, int costPoints, String remark) {
        if (costPoints <= 0) {
            return;
        }
        ensureEnoughCustom(userId, costPoints);
        int before = currentPoints(userId);
        int updated = userMapper.deductPoints(userId, costPoints);
        if (updated <= 0) {
            throw new PointsNotEnoughException(before, costPoints);
        }
        UserAccount after = requireUser(userId);
        int afterPoints = value(after.getPoints());
        recordPointsLog(userId, -costPoints, before, afterPoints, featureCode);

        PointTransaction transaction = new PointTransaction();
        transaction.setUserId(userId);
        transaction.setJobId(jobId);
        transaction.setFeatureCode(featureCode);
        transaction.setFeatureName(featureName);
        transaction.setPointsChange(-costPoints);
        transaction.setBalanceAfter(afterPoints);
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
            if (dto.getCostPoints() < 0) {
                throw new IllegalArgumentException("\u529f\u80fd\u79ef\u5206\u4e0d\u80fd\u4e3a\u8d1f\u6570");
            }
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
        ensureFeatureEnabled(pricing);
        if (balance < cost) {
            throw new PointsNotEnoughException(balance, cost);
        }
    }

    private void ensureFeatureEnabled(FeaturePricing pricing) {
        if (!Boolean.TRUE.equals(pricing.getEnabled())) {
            throw new IllegalStateException("\u8be5\u529f\u80fd\u6682\u672a\u542f\u7528");
        }
    }

    private void deduct(Long userId, FeaturePricing pricing, String remark) {
        int cost = value(pricing.getCostPoints());
        if (cost <= 0) return;
        int before = currentPoints(userId);
        int updated = userMapper.deductPoints(userId, cost);
        if (updated <= 0) throw new PointsNotEnoughException(before, cost);
        UserAccount after = requireUser(userId);
        int afterPoints = value(after.getPoints());
        recordPointsLog(userId, -cost, before, afterPoints, pricing.getFeatureCode());

        PointTransaction transaction = new PointTransaction();
        transaction.setUserId(userId);
        transaction.setJobId(null);
        transaction.setFeatureCode(pricing.getFeatureCode());
        transaction.setFeatureName(pricing.getFeatureName());
        transaction.setPointsChange(-cost);
        transaction.setBalanceAfter(afterPoints);
        transaction.setRemark(remark == null ? "" : remark);
        transaction.setCreatedAt(LocalDateTime.now());
        transactionMapper.insert(transaction);
    }

    private FeaturePricing requirePricing(String featureCode) {
        FeaturePricing pricing = pricingMapper.selectOne(new LambdaQueryWrapper<FeaturePricing>()
                .eq(FeaturePricing::getFeatureCode, featureCode));
        if (pricing == null) throw new IllegalStateException("\u529f\u80fd\u4ef7\u683c\u672a\u914d\u7f6e\uff1a" + featureCode);
        return pricing;
    }

    private UserAccount requireUser(Long userId) {
        UserAccount user = userMapper.selectById(userId);
        if (user == null) throw new IllegalStateException("\u7528\u6237\u4e0d\u5b58\u5728");
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
            throw new IllegalStateException("\u65e0\u7ba1\u7406\u5458\u6743\u9650");
        }
    }

    private void recordPointsLog(Long userId, int change, int before, int after, String reason) {
        if (pointsLogMapper == null) {
            return;
        }
        UserPointsLog log = new UserPointsLog();
        log.setUserId(userId);
        log.setChangeAmount(change);
        log.setBeforePoints(before);
        log.setAfterPoints(after);
        log.setReason(reason);
        log.setCreatedAt(LocalDateTime.now());
        pointsLogMapper.insert(log);
    }

    private String pointsNotEnoughMessage(int balance, int cost) {
        int missing = Math.max(0, cost - balance);
        return "\u79ef\u5206\u4e0d\u8db3\uff0c\u5f53\u524d\u79ef\u5206\uff1a" + balance +
                "\uff0c\u6240\u9700\u79ef\u5206\uff1a" + cost +
                "\uff0c\u8fd8\u5dee\uff1a" + missing + "\u79ef\u5206";
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }
}
