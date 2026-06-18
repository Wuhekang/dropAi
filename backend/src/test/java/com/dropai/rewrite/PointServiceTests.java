package com.dropai.rewrite;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.dto.FeaturePricingUpdateDTO;
import com.dropai.rewrite.entity.FeaturePricing;
import com.dropai.rewrite.entity.PointTransaction;
import com.dropai.rewrite.entity.UserAccount;
import com.dropai.rewrite.mapper.FeaturePricingMapper;
import com.dropai.rewrite.mapper.PointTransactionMapper;
import com.dropai.rewrite.mapper.UserAccountMapper;
import com.dropai.rewrite.service.PointService;
import com.dropai.rewrite.service.PointsNotEnoughException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PointServiceTests {
    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void successfulActionDeductsPointsAndWritesTransaction() {
        UserAccountMapper userMapper = mock(UserAccountMapper.class);
        FeaturePricingMapper pricingMapper = mock(FeaturePricingMapper.class);
        PointTransactionMapper txMapper = mock(PointTransactionMapper.class);
        PointService service = new PointService(userMapper, pricingMapper, txMapper);
        AuthContext.setUserId(7L);

        FeaturePricing pricing = pricing("DESIGN_GENERATE", "毕业设计成果包生成", 100, true);
        UserAccount before = user(7L, 1000, 1000, 0, "USER");
        UserAccount after = user(7L, 900, 1000, 100, "USER");
        when(pricingMapper.selectOne(any())).thenReturn(pricing);
        when(userMapper.selectById(7L)).thenReturn(before, after);
        when(userMapper.deductPoints(7L, 100)).thenReturn(1);

        String result = service.chargeAfterSuccess("DESIGN_GENERATE", "测试扣费", () -> "OK");

        assertEquals("OK", result);
        ArgumentCaptor<PointTransaction> captor = ArgumentCaptor.forClass(PointTransaction.class);
        verify(txMapper).insert(captor.capture());
        assertEquals(-100, captor.getValue().getPointsChange());
        assertEquals(900, captor.getValue().getBalanceAfter());
        assertEquals("测试扣费", captor.getValue().getRemark());
    }

    @Test
    void insufficientPointsBlocksAction() {
        UserAccountMapper userMapper = mock(UserAccountMapper.class);
        FeaturePricingMapper pricingMapper = mock(FeaturePricingMapper.class);
        PointTransactionMapper txMapper = mock(PointTransactionMapper.class);
        PointService service = new PointService(userMapper, pricingMapper, txMapper);
        AuthContext.setUserId(8L);

        when(pricingMapper.selectOne(any())).thenReturn(pricing("CAD_GENERATE", "CAD图纸生成", 50, true));
        when(userMapper.selectById(8L)).thenReturn(user(8L, 10, 1000, 990, "USER"));

        assertThrows(PointsNotEnoughException.class,
                () -> service.chargeAfterSuccess("CAD_GENERATE", "余额不足测试", () -> "SHOULD_NOT_RUN"));
    }

    @Test
    void adminCanUpdatePricing() {
        UserAccountMapper userMapper = mock(UserAccountMapper.class);
        FeaturePricingMapper pricingMapper = mock(FeaturePricingMapper.class);
        PointTransactionMapper txMapper = mock(PointTransactionMapper.class);
        PointService service = new PointService(userMapper, pricingMapper, txMapper);
        AuthContext.setUserId(1L);

        FeaturePricing pricing = pricing("DOCX_GENERATE", "文档生成", 30, true);
        when(userMapper.selectById(1L)).thenReturn(user(1L, 1000, 1000, 0, "ADMIN"));
        when(pricingMapper.selectOne(any())).thenReturn(pricing);
        FeaturePricingUpdateDTO dto = new FeaturePricingUpdateDTO();
        dto.setCostPoints(40);
        dto.setEnabled(false);

        assertEquals(40, service.updatePricing("DOCX_GENERATE", dto).costPoints());
        assertEquals(false, service.updatePricing("DOCX_GENERATE", dto).enabled());
    }

    private FeaturePricing pricing(String code, String name, int cost, boolean enabled) {
        FeaturePricing pricing = new FeaturePricing();
        pricing.setId(1L);
        pricing.setFeatureCode(code);
        pricing.setFeatureName(name);
        pricing.setCostPoints(cost);
        pricing.setEnabled(enabled);
        return pricing;
    }

    private UserAccount user(Long id, int points, int total, int used, String role) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setPhone("12345678901");
        user.setRole(role);
        user.setPoints(points);
        user.setTotalPoints(total);
        user.setUsedPoints(used);
        return user;
    }
}
