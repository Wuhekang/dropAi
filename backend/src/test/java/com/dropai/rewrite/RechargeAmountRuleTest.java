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
}
