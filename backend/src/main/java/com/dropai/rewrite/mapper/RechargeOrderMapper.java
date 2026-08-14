package com.dropai.rewrite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dropai.rewrite.entity.RechargeOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface RechargeOrderMapper extends BaseMapper<RechargeOrder> {
    @Update("UPDATE recharge_order SET status='processing', updated_at=CURRENT_TIMESTAMP WHERE id=#{id} AND status='pending'")
    int claimPending(@Param("id") Long id);
}
