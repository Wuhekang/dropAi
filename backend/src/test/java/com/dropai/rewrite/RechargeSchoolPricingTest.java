package com.dropai.rewrite;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.dto.RechargeOrderCreateDTO;
import com.dropai.rewrite.entity.RechargeOrder;
import com.dropai.rewrite.entity.School;
import com.dropai.rewrite.entity.UserAccount;
import com.dropai.rewrite.mapper.PointTransactionMapper;
import com.dropai.rewrite.mapper.RechargeOrderMapper;
import com.dropai.rewrite.mapper.SchoolMapper;
import com.dropai.rewrite.mapper.UserAccountMapper;
import com.dropai.rewrite.mapper.UserPointsLogMapper;
import com.dropai.rewrite.service.EpayService;
import com.dropai.rewrite.service.RechargeReconciliationAuditService;
import com.dropai.rewrite.service.RechargeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RechargeSchoolPricingTest {
    private final RechargeOrderMapper orders = mock(RechargeOrderMapper.class);
    private final UserAccountMapper users = mock(UserAccountMapper.class);
    private final SchoolMapper schools = mock(SchoolMapper.class);
    private final UserPointsLogMapper logs = mock(UserPointsLogMapper.class);
    private final PointTransactionMapper transactions = mock(PointTransactionMapper.class);
    private final EpayService epay = mock(EpayService.class);
    private final RechargeService service = new RechargeService(orders, users, schools, logs, transactions, epay,
            mock(RechargeReconciliationAuditService.class));

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void boundUserUsesSchoolPriceAndOrderSnapshotSurvivesLaterPriceChange() {
        UserAccount user = user(7L, "USER", 1L, 0);
        School school = school(1L, "测试学校", "0.30");
        when(users.selectById(7L)).thenReturn(user);
        when(users.selectOne(any())).thenReturn(user);
        when(schools.selectOne(any())).thenReturn(school);
        when(users.addPoints(7L, 10)).thenReturn(1);
        AuthContext.setUserId(7L);

        RechargeOrderCreateDTO input = new RechargeOrderCreateDTO();
        input.setAmount(new BigDecimal("2.00"));
        service.createOrder(input);

        var lockOrder = inOrder(schools, users, orders);
        lockOrder.verify(schools).selectOne(any());
        lockOrder.verify(users).selectOne(any());
        lockOrder.verify(orders).insert(any(RechargeOrder.class));

        ArgumentCaptor<RechargeOrder> captor = ArgumentCaptor.forClass(RechargeOrder.class);
        verify(orders).insert(captor.capture());
        RechargeOrder order = captor.getValue();
        assertEquals(10, order.getPoints());
        assertEquals(new BigDecimal("2.00"), order.getRechargePricePer10());
        assertEquals(1L, order.getSchoolId());

        school.setRechargePricePer10(new BigDecimal("0.50"));
        UserAccount before = user(7L, "USER", 1L, 0);
        UserAccount after = user(7L, "USER", 1L, 10);
        when(users.selectById(7L)).thenReturn(before, after);
        when(epay.verifyNotify(anyMap())).thenReturn(true);
        when(orders.selectOne(any())).thenReturn(order);
        when(orders.claimPending(order.getId())).thenReturn(1);

        assertEquals("success", service.handleNotify(notifyParams("2.00"), "test"));
        verify(users).addPoints(7L, 10);
        verify(schools, times(1)).selectOne(any());
    }

    @Test
    void schoolViewerGetsSchoolPriceAndHigherMaximum() {
        when(users.selectById(8L)).thenReturn(user(8L, "SCHOOL_VIEWER", 1L, 0));
        when(schools.selectById(1L)).thenReturn(school(1L, "测试学校", "0.30", "0.80"));
        AuthContext.setUserId(8L);

        Map<String, Object> pricing = service.pricing();
        assertTrue((Boolean) pricing.get("schoolPricing"));
        assertEquals(new BigDecimal("0.30"), pricing.get("pricePer10"));
        assertEquals(new BigDecimal("100000.00"), pricing.get("maxAmount"));
        assertEquals(333, service.plans().get(0).points());
    }

    @Test
    void studentRetailPriceDoesNotChangeSchoolViewerPurchasePrice() {
        School school = school(1L, "测试学校", "0.30", "0.60", "0.30");
        when(users.selectById(20L)).thenReturn(user(20L, "SCHOOL_VIEWER", 1L, 0));
        when(users.selectById(21L)).thenReturn(user(21L, "USER", 1L, 0));
        when(schools.selectById(1L)).thenReturn(school);

        AuthContext.setUserId(20L);
        Map<String, Object> viewerPricing = service.pricing();
        assertEquals(new BigDecimal("0.30"), viewerPricing.get("pricePer10"));
        assertEquals(new BigDecimal("0.30"), viewerPricing.get("minAmount"));

        AuthContext.setUserId(21L);
        Map<String, Object> studentPricing = service.pricing();
        assertEquals(new BigDecimal("0.60"), studentPricing.get("pricePer10"));
        assertEquals(new BigDecimal("0.60"), studentPricing.get("minAmount"));
        assertEquals(new BigDecimal("1000.00"), studentPricing.get("maxAmount"));
    }

    @Test
    void unboundUserKeepsTwoYuanPerTenPoints() {
        when(users.selectById(9L)).thenReturn(user(9L, "USER", 0L, 0));
        when(users.selectOne(any())).thenReturn(user(9L, "USER", 0L, 0));
        AuthContext.setUserId(9L);

        Map<String, Object> pricing = service.pricing();
        assertFalse((Boolean) pricing.get("schoolPricing"));
        assertEquals(new BigDecimal("2.00"), pricing.get("pricePer10"));
        assertEquals(50, service.plans().get(0).points());

        RechargeOrderCreateDTO input = new RechargeOrderCreateDTO();
        input.setAmount(new BigDecimal("10"));
        service.createOrder(input);
        ArgumentCaptor<RechargeOrder> captor = ArgumentCaptor.forClass(RechargeOrder.class);
        verify(orders).insert(captor.capture());
        assertEquals(new BigDecimal("2.00"), captor.getValue().getRechargePricePer10());
        assertEquals(50, captor.getValue().getPoints());
    }

    @Test
    void boundUserDefaultsToTwoYuanAndPriceMustRespectAdminFloor() {
        UserAccount user = user(10L, "USER", 1L, 0);
        School school = school(1L, "测试学校", "0.30");
        when(users.selectById(10L)).thenReturn(user);
        when(schools.selectById(1L)).thenReturn(school);
        AuthContext.setUserId(10L);

        assertEquals(new BigDecimal("2.00"), service.pricing().get("pricePer10"));

        school.setStudentRechargePricePer10(new BigDecimal("0.80"));
        assertThrows(IllegalStateException.class, service::pricing);
    }

    @Test
    void boundUserCanRechargeAtThirtyCentsAfterAdminLowersFloorToThirtyCents() {
        UserAccount user = user(11L, "USER", 1L, 0);
        School school = school(1L, "测试学校", "0.30", "0.30", "0.30");
        when(users.selectById(11L)).thenReturn(user);
        when(users.selectOne(any())).thenReturn(user);
        when(schools.selectById(1L)).thenReturn(school);
        when(schools.selectOne(any())).thenReturn(school);
        AuthContext.setUserId(11L);

        assertEquals(new BigDecimal("0.30"), service.pricing().get("pricePer10"));

        RechargeOrderCreateDTO input = new RechargeOrderCreateDTO();
        input.setAmount(new BigDecimal("0.30"));
        service.createOrder(input);

        ArgumentCaptor<RechargeOrder> captor = ArgumentCaptor.forClass(RechargeOrder.class);
        verify(orders).insert(captor.capture());
        assertEquals(new BigDecimal("0.30"), captor.getValue().getRechargePricePer10());
        assertEquals(10, captor.getValue().getPoints());
    }

    @Test
    void createOrderRevalidatesSchoolAfterTakingSchoolThenUserLocks() {
        UserAccount hint = user(30L, "USER", 1L, 0);
        UserAccount moved = user(30L, "USER", 2L, 0);
        when(users.selectById(30L)).thenReturn(hint);
        when(schools.selectOne(any())).thenReturn(school(1L, "原学校", "0.30"));
        when(users.selectOne(any())).thenReturn(moved);
        AuthContext.setUserId(30L);
        RechargeOrderCreateDTO input = new RechargeOrderCreateDTO();
        input.setAmount(new BigDecimal("0.30"));

        assertThrows(IllegalStateException.class, () -> service.createOrder(input));

        var lockOrder = inOrder(schools, users);
        lockOrder.verify(schools).selectOne(any());
        lockOrder.verify(users).selectOne(any());
        verify(orders, never()).insert(any(RechargeOrder.class));
    }

    private UserAccount user(Long id, String role, Long schoolId, int points) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setRole(role);
        user.setSchoolId(schoolId);
        user.setAccountEnabled(true);
        user.setPoints(points);
        return user;
    }

    private School school(Long id, String name, String price) {
        return school(id, name, price, "2.00", "1.00");
    }

    private School school(Long id, String name, String price, String studentPrice) {
        return school(id, name, price, studentPrice, "1.00");
    }

    private School school(Long id, String name, String price, String studentPrice, String studentMinPrice) {
        School school = new School();
        school.setId(id);
        school.setSchoolName(name);
        school.setEnabled(true);
        school.setRechargePricePer10(new BigDecimal(price));
        school.setStudentRechargePricePer10(new BigDecimal(studentPrice));
        school.setStudentRechargeMinPricePer10(new BigDecimal(studentMinPrice));
        return school;
    }

    private Map<String, String> notifyParams(String amount) {
        Map<String, String> params = new HashMap<>();
        params.put("out_trade_no", "R1");
        params.put("trade_no", "T1");
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("money", amount);
        params.put("sign", "valid");
        return params;
    }
}
