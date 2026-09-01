package com.dropai.rewrite;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.dto.RechargeConfirmDTO;
import com.dropai.rewrite.entity.RechargeOrder;
import com.dropai.rewrite.entity.UserAccount;
import com.dropai.rewrite.mapper.*;
import com.dropai.rewrite.service.EpayService;
import com.dropai.rewrite.service.RechargeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.dropai.rewrite.service.RechargeReconciliationAuditService;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RechargeNotifySecurityTest {
    @AfterEach void clearAuth() { AuthContext.clear(); }

    @Test void successfulNotifyCreditsExactlyOnce() {
        RechargeOrderMapper orders=mock(RechargeOrderMapper.class); UserAccountMapper users=mock(UserAccountMapper.class);
        UserPointsLogMapper logs=mock(UserPointsLogMapper.class); PointTransactionMapper transactions=mock(PointTransactionMapper.class);
        EpayService epay=mock(EpayService.class); RechargeOrder order=order("pending");
        when(epay.verifyNotify(anyMap())).thenReturn(true); when(orders.selectOne(any())).thenReturn(order);
        when(orders.claimPending(1L)).thenReturn(1); UserAccount before=user(0),after=user(50);
        when(users.selectById(7L)).thenReturn(before,after); when(users.addPoints(7L,50)).thenReturn(1);
        RechargeService service=new RechargeService(orders,users,mock(com.dropai.rewrite.mapper.SchoolMapper.class),logs,transactions,epay,mock(RechargeReconciliationAuditService.class));
        assertEquals("success",service.handleNotify(params("10.00"),"127.0.0.1"));
        verify(users,times(1)).addPoints(7L,50); verify(transactions,times(1)).insert(any(com.dropai.rewrite.entity.PointTransaction.class));
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

    @Test void legacySchoolOrderWithoutPriceSnapshotUsesStoredPositivePoints() {
        RechargeOrderMapper orders=mock(RechargeOrderMapper.class); UserAccountMapper users=mock(UserAccountMapper.class);
        UserPointsLogMapper logs=mock(UserPointsLogMapper.class); PointTransactionMapper transactions=mock(PointTransactionMapper.class);
        EpayService epay=mock(EpayService.class); RechargeOrder order=order("pending");
        order.setAmount(new BigDecimal("0.30")); order.setPoints(10); order.setRechargePricePer10(null);
        when(epay.verifyNotify(anyMap())).thenReturn(true); when(orders.selectOne(any())).thenReturn(order);
        when(orders.claimPending(1L)).thenReturn(1); when(users.addPoints(7L,10)).thenReturn(1);
        when(users.selectById(7L)).thenReturn(user(0),user(10));
        RechargeService service=new RechargeService(orders,users,mock(SchoolMapper.class),logs,transactions,epay,mock(RechargeReconciliationAuditService.class));

        assertEquals("success",service.handleNotify(params("0.30"),"test"));
        verify(users).addPoints(7L,10);
    }

    @Test void confirmedOrderCanBeClaimedBySignedNotifyAndCreditsExactlyOnce() {
        RechargeOrderMapper orders=mock(RechargeOrderMapper.class); UserAccountMapper users=mock(UserAccountMapper.class);
        UserPointsLogMapper logs=mock(UserPointsLogMapper.class); PointTransactionMapper transactions=mock(PointTransactionMapper.class);
        EpayService epay=mock(EpayService.class); RechargeOrder order=order("pending");
        when(epay.verifyNotify(anyMap())).thenReturn(true); when(orders.selectOne(any())).thenReturn(order);
        when(orders.confirmPending(eq(1L),eq(7L),eq(new BigDecimal("10.00")),eq("1234"),isNull(),any())).thenReturn(1);
        when(orders.claimPending(1L)).thenReturn(1); when(users.addPoints(7L,50)).thenReturn(1);
        when(users.selectById(7L)).thenReturn(user(0),user(50));
        RechargeService service=new RechargeService(orders,users,mock(SchoolMapper.class),logs,transactions,epay,mock(RechargeReconciliationAuditService.class));
        RechargeConfirmDTO confirm=new RechargeConfirmDTO(); confirm.setOrderNo("R1");
        confirm.setPayAmount(new BigDecimal("10.00")); confirm.setPayAccountLast4("1234");
        AuthContext.setUserId(7L);

        service.confirmPayment(confirm);
        assertEquals("waiting_review",order.getStatus());
        assertEquals("success",service.handleNotify(params("10.00"),"test"));
        assertEquals("success",service.handleNotify(params("10.00"),"test"));

        verify(orders,times(1)).claimPending(1L);
        verify(users,times(1)).addPoints(7L,50);
        verify(transactions,times(1)).insert(any(com.dropai.rewrite.entity.PointTransaction.class));
        assertEquals("paid",order.getStatus());
    }

    @Test void staleConfirmDoesNotOverwriteConcurrentPaidOrder() {
        RechargeOrderMapper orders=mock(RechargeOrderMapper.class); RechargeOrder initial=order("pending"), latest=order("paid");
        when(orders.selectOne(any())).thenReturn(initial,latest); when(orders.confirmPending(anyLong(),anyLong(),any(),anyString(),isNull(),any())).thenReturn(0);
        RechargeService service=new RechargeService(orders,mock(UserAccountMapper.class),mock(SchoolMapper.class),mock(UserPointsLogMapper.class),mock(PointTransactionMapper.class),mock(EpayService.class),mock(RechargeReconciliationAuditService.class));
        AuthContext.setUserId(7L);

        assertEquals("paid",service.confirmPayment(confirm()).status());
        assertEquals("pending",initial.getStatus());
        verify(orders,never()).updateById(any(RechargeOrder.class));
    }

    @Test void staleConfirmDoesNotOverwriteConcurrentProcessingOrder() {
        RechargeOrderMapper orders=mock(RechargeOrderMapper.class); RechargeOrder initial=order("pending"), latest=order("processing");
        when(orders.selectOne(any())).thenReturn(initial,latest); when(orders.confirmPending(anyLong(),anyLong(),any(),anyString(),isNull(),any())).thenReturn(0);
        RechargeService service=new RechargeService(orders,mock(UserAccountMapper.class),mock(SchoolMapper.class),mock(UserPointsLogMapper.class),mock(PointTransactionMapper.class),mock(EpayService.class),mock(RechargeReconciliationAuditService.class));
        AuthContext.setUserId(7L);

        assertThrows(IllegalStateException.class,()->service.confirmPayment(confirm()));
        assertEquals("pending",initial.getStatus());
        verify(orders,never()).updateById(any(RechargeOrder.class));
    }

    @Test void lostClaimRereadsConcurrentCompletedOrderAndReturnsSuccessWithoutCrediting() {
        RechargeOrderMapper orders=mock(RechargeOrderMapper.class); UserAccountMapper users=mock(UserAccountMapper.class);
        EpayService epay=mock(EpayService.class); RechargeOrder initial=order("pending"), latest=order("paid");
        when(epay.verifyNotify(anyMap())).thenReturn(true);
        when(orders.selectOne(any())).thenReturn(initial,latest); when(orders.claimPending(1L)).thenReturn(0);
        RechargeService service=new RechargeService(orders,users,mock(SchoolMapper.class),mock(UserPointsLogMapper.class),mock(PointTransactionMapper.class),epay,mock(RechargeReconciliationAuditService.class));

        assertEquals("success",service.handleNotify(params("10.00"),"test"));
        verify(users,never()).addPoints(anyLong(),anyInt());
        verify(orders,never()).updateById(any(RechargeOrder.class));
    }

    @Test void lostClaimRereadsNonterminalOrderAndReturnsFailWithoutCrediting() {
        RechargeOrderMapper orders=mock(RechargeOrderMapper.class); UserAccountMapper users=mock(UserAccountMapper.class);
        EpayService epay=mock(EpayService.class); RechargeOrder initial=order("pending"), latest=order("processing");
        when(epay.verifyNotify(anyMap())).thenReturn(true);
        when(orders.selectOne(any())).thenReturn(initial,latest); when(orders.claimPending(1L)).thenReturn(0);
        RechargeService service=new RechargeService(orders,users,mock(SchoolMapper.class),mock(UserPointsLogMapper.class),mock(PointTransactionMapper.class),epay,mock(RechargeReconciliationAuditService.class));

        assertEquals("fail",service.handleNotify(params("10.00"),"test"));
        verify(users,never()).addPoints(anyLong(),anyInt());
        verify(orders,never()).updateById(any(RechargeOrder.class));
    }

    private RechargeOrder order(String status){RechargeOrder o=new RechargeOrder();o.setId(1L);o.setUserId(7L);o.setOrderNo("R1");o.setAmount(new BigDecimal("10.00"));o.setRechargePricePer10(new BigDecimal("2.00"));o.setPoints(50);o.setStatus(status);return o;}
    private RechargeConfirmDTO confirm(){RechargeConfirmDTO dto=new RechargeConfirmDTO();dto.setOrderNo("R1");dto.setPayAmount(new BigDecimal("10.00"));dto.setPayAccountLast4("1234");return dto;}
    private UserAccount user(int points){UserAccount u=new UserAccount();u.setId(7L);u.setPoints(points);u.setAccountEnabled(true);return u;}
    private Map<String,String> params(String money){Map<String,String> p=new HashMap<>();p.put("pid","1000");p.put("out_trade_no","R1");p.put("trade_no","T123");p.put("trade_status","TRADE_SUCCESS");p.put("money",money);p.put("sign","valid");return p;}
}
