package com.dropai.rewrite.external;

import com.dropai.rewrite.entity.PointTransaction;
import com.dropai.rewrite.entity.UserAccount;
import com.dropai.rewrite.entity.UserPointsLog;
import com.dropai.rewrite.mapper.PointTransactionMapper;
import com.dropai.rewrite.mapper.UserAccountMapper;
import com.dropai.rewrite.mapper.UserPointsLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Keeps external-route refunds isolated from the unchanged default PointService. */
@Service
public class XuejieExternalPointRefundService {
    private static final String REFUND_SUFFIX = "_XUEJIE_REFUND";

    private final UserAccountMapper userAccountMapper;
    private final PointTransactionMapper transactionMapper;
    private final UserPointsLogMapper pointsLogMapper;
    private final XuejieExternalJobStateRepository stateRepository;

    public XuejieExternalPointRefundService(UserAccountMapper userAccountMapper,
                                            PointTransactionMapper transactionMapper,
                                            UserPointsLogMapper pointsLogMapper,
                                            XuejieExternalJobStateRepository stateRepository) {
        this.userAccountMapper = userAccountMapper;
        this.transactionMapper = transactionMapper;
        this.pointsLogMapper = pointsLogMapper;
        this.stateRepository = stateRepository;
    }

    @Transactional
    public boolean refundIfNeeded(Long userId, String jobId, String originalFeatureCode,
                                  String featureName, int points, String reason) {
        if (points <= 0) return false;
        String refundFeatureCode = originalFeatureCode + REFUND_SUFFIX;
        if (!stateRepository.claimRefund(jobId)) return false;

        UserAccount beforeAccount = userAccountMapper.selectById(userId);
        if (beforeAccount == null) throw new IllegalStateException("外部任务退积分失败：用户不存在");
        int before = value(beforeAccount.getPoints());
        if (userAccountMapper.refundPoints(userId, points) <= 0) {
            throw new IllegalStateException("外部任务退积分失败");
        }
        UserAccount afterAccount = userAccountMapper.selectById(userId);
        int after = afterAccount == null ? before + points : value(afterAccount.getPoints());

        UserPointsLog log = new UserPointsLog();
        log.setUserId(userId);
        log.setChangeAmount(points);
        log.setBeforePoints(before);
        log.setAfterPoints(after);
        log.setReason(refundFeatureCode);
        log.setCreatedAt(LocalDateTime.now());
        pointsLogMapper.insert(log);

        PointTransaction transaction = new PointTransaction();
        transaction.setUserId(userId);
        transaction.setJobId(jobId);
        transaction.setFeatureCode(refundFeatureCode);
        transaction.setFeatureName(featureName + "退款");
        transaction.setPointsChange(points);
        transaction.setBalanceAfter(after);
        transaction.setRemark(truncate(reason));
        transaction.setCreatedAt(LocalDateTime.now());
        transactionMapper.insert(transaction);
        stateRepository.refunded(jobId);
        return true;
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String truncate(String value) {
        String normalized = value == null ? "" : value;
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 240);
    }
}
