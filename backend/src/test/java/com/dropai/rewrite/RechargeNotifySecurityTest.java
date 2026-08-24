package com.dropai.rewrite;

import com.dropai.rewrite.entity.RechargeOrder;
import com.dropai.rewrite.entity.UserAccount;
import com.dropai.rewrite.mapper.*;
import com.dropai.rewrite.service.EpayService;
import com.dropai.rewrite.service.RechargeService;
import org.junit.jupiter.api.Test;
import com.dropai.rewrite.service.RechargeReconciliationAuditService;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RechargeNotifySecurityTest {
    @Test void successfulNotifyCreditsExactlyOnce() {
        RechargeOrderMapper orders=mock(RechargeOrderMapper.class); UserAccountMapper users=mock(UserAccountMapper.class);
        UserPointsLogMapper logs=mock(UserPointsLogMapper.class); PointTransactionMapper transactions=mock(PointTransactionMapper.class);
        EpayService epay=mock(EpayService.class); RechargeOrder order=order("pending");
        when(epay.verifyNotify(anyMap())).thenReturn(true); when(orders.selectOne(any())).thenReturn(order);
        when(orders.claimPending(1L)).thenReturn(1); UserAccount before=user(0),after=user(100);
        when(users.selectById(7L)).thenReturn(before,after);
        RechargeService service=new RechargeService(orders,users,mock(com.dropai.rewrite.mapper.SchoolMapper.class),logs,transactions,epay,mock(RechargeReconciliationAuditService.class));
        assertEquals("success",service.handleNotify(params("10.00"),"127.0.0.1"));
        verify(users,times(1)).addPoints(7L,100); verify(transactions,times(1)).insert(any(com.dropai.rewrite.entity.PointTransaction.class));
        assertEquals("paid",order.getStatus()); assertEquals("T123",order.getThirdPartyTradeNo());
    }

    @Test void paidDuplicateAndForgedAmountNeverCredit() {
        RechargeOrderMapper orders=mock(RechargeOrderMapper.class); UserAccountMapper users=mock(UserAccountMapper.class);
        EpayService epay=mock(EpayService.class); when(epay.verifyNotify(anyMap())).thenReturn(true);
        RechargeService service=new RechargeService(orders,users,mock(com.dropai.rewrite.mapper.SchoolMapper.class),mock(UserPointsLogMapper.class),mock(PointTransactionMapper.class),epay,mock(RechargeReconciliationAuditService.class));
        when(orders.selectOne(any())).thenReturn(order("paid"));
        assertEquals("success",service.handleNotify(params("10.00"),"test")); verify(users,never()).addPoints(anyLong(),anyInt());
        when(orders.selectOne(any())).thenReturn(order("pending"));
        assertEquals("fail",service.handleNotify(params("9.99"),"test")); verify(users,never()).addPoints(anyLong(),anyInt());
    }

    private RechargeOrder order(String status){RechargeOrder o=new RechargeOrder();o.setId(1L);o.setUserId(7L);o.setOrderNo("R1");o.setAmount(new BigDecimal("10.00"));o.setPoints(100);o.setStatus(status);return o;}
    private UserAccount user(int points){UserAccount u=new UserAccount();u.setId(7L);u.setPoints(points);return u;}
    private Map<String,String> params(String money){Map<String,String> p=new HashMap<>();p.put("pid","1000");p.put("out_trade_no","R1");p.put("trade_no","T123");p.put("trade_status","TRADE_SUCCESS");p.put("money",money);p.put("sign","valid");return p;}
}
