package com.dropai.rewrite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dropai.rewrite.entity.RechargeOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface RechargeOrderMapper extends BaseMapper<RechargeOrder> {
    @Update("UPDATE recharge_order SET pay_amount=#{payAmount}, pay_account_last4=#{payAccountLast4}, " +
            "proof_image=#{proofImage}, status='waiting_review', updated_at=#{updatedAt} " +
            "WHERE id=#{id} AND user_id=#{userId} AND status='pending'")
    int confirmPending(@Param("id") Long id, @Param("userId") Long userId,
                       @Param("payAmount") BigDecimal payAmount,
                       @Param("payAccountLast4") String payAccountLast4,
                       @Param("proofImage") String proofImage,
                       @Param("updatedAt") LocalDateTime updatedAt);

    @Update("UPDATE recharge_order SET status='processing', updated_at=CURRENT_TIMESTAMP WHERE id=#{id} AND status IN ('pending','waiting_review')")
    int claimPending(@Param("id") Long id);
}
