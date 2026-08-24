package com.dropai.rewrite;

import com.dropai.rewrite.service.RechargeService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RechargeAmountRuleTest {
    private RechargeService service() {
        return new RechargeService(mock(com.dropai.rewrite.mapper.RechargeOrderMapper.class),
                mock(com.dropai.rewrite.mapper.UserAccountMapper.class),
                mock(com.dropai.rewrite.mapper.SchoolMapper.class),
                mock(com.dropai.rewrite.mapper.UserPointsLogMapper.class),
                mock(com.dropai.rewrite.mapper.PointTransactionMapper.class),
                mock(com.dropai.rewrite.service.EpayService.class), mock(com.dropai.rewrite.service.RechargeReconciliationAuditService.class));
    }
    @Test void acceptsIntegerAmountsFromOneToOneHundred() {
        for (int value : new int[]{1,10,20,100,999,1000})
            assertEquals(new BigDecimal(value + ".00"), service().validateAmount(BigDecimal.valueOf(value)));
    }
    @Test void rejectsInvalidAmounts() {
        for (String value : new String[]{"0","-1","1.5","1001"})
            assertThrows(IllegalArgumentException.class, () -> service().validateAmount(new BigDecimal(value)));
        assertThrows(IllegalArgumentException.class, () -> service().validateAmount(null));
    }
    @Test void schoolRechargeAcceptsCentsAndUsesSpecialRate() {
        RechargeService recharge = service();
        assertEquals(new BigDecimal("0.30"), recharge.validateSchoolAmount(new BigDecimal("0.30")));
        assertEquals(10, recharge.calculateRechargePoints(new BigDecimal("0.30"), true));
        assertEquals(33, recharge.calculateRechargePoints(new BigDecimal("1.00"), true));
        assertEquals(1000, recharge.calculateRechargePoints(new BigDecimal("30.00"), true));
        assertEquals(200, recharge.calculateRechargePoints(new BigDecimal("10.00"), new BigDecimal("0.50")));
        assertThrows(IllegalArgumentException.class, () -> recharge.validateSchoolAmount(new BigDecimal("0.29")));
        assertThrows(IllegalArgumentException.class, () -> recharge.validateSchoolAmount(new BigDecimal("1.001")));
    }
}
