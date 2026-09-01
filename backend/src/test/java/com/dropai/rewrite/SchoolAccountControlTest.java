package com.dropai.rewrite;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.entity.School;
import com.dropai.rewrite.entity.PointTransaction;
import com.dropai.rewrite.entity.UserAccount;
import com.dropai.rewrite.mapper.PointTransactionMapper;
import com.dropai.rewrite.mapper.SchoolMapper;
import com.dropai.rewrite.mapper.UserAccountMapper;
import com.dropai.rewrite.service.AccountSecurityService;
import com.dropai.rewrite.service.SchoolService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchoolAccountControlTest {
    private static final String TEST_PASSWORD_HASH = new BCryptPasswordEncoder(4).encode("secret12");
    private final SchoolMapper schools = mock(SchoolMapper.class);
    private final UserAccountMapper users = mock(UserAccountMapper.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PointTransactionMapper transactions = mock(PointTransactionMapper.class);
    private final AccountSecurityService security = mock(AccountSecurityService.class);
    private final SchoolService service = new SchoolService(schools, users, jdbc, transactions, security);

    @BeforeAll
    static void initializeLambdaMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "school-control-test");
        assistant.setCurrentNamespace("com.dropai.rewrite.SchoolAccountControlTest");
        TableInfoHelper.initTableInfo(assistant, School.class);
        TableInfoHelper.initTableInfo(assistant, UserAccount.class);
    }

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void schoolPriceHasThirtyCentFloor() {
        assertEquals(new BigDecimal("0.30"), service.validateRechargePrice(new BigDecimal("0.300")));
        assertThrows(IllegalArgumentException.class,
                () -> service.validateRechargePrice(new BigDecimal("0.29")));
        assertThrows(IllegalArgumentException.class,
                () -> service.validateStudentRechargePrice(new BigDecimal("0.49"), new BigDecimal("0.50")));
        assertEquals(new BigDecimal("0.50"),
                service.validateStudentRechargePrice(new BigDecimal("0.50"), new BigDecimal("0.50")));
    }

    @Test
    void schoolViewerUpdatesOnlyStudentPriceAndReceivesEffectiveFloor() {
        UserAccount viewer = user(1L, "SCHOOL_VIEWER", 10L, 0);
        School school = school(10L);
        school.setRechargePricePer10(new BigDecimal("0.50"));
        school.setStudentRechargePricePer10(new BigDecimal("0.50"));
        when(users.selectById(1L)).thenReturn(viewer);
        when(schools.selectOne(any())).thenReturn(school);
        when(users.selectOne(any())).thenReturn(viewer);
        when(schools.update(isNull(), any())).thenReturn(1);
        AuthContext.setUserId(1L);

        var result = service.updateRechargePrice(new SchoolService.PriceInput(new BigDecimal("0.60")));

        assertEquals(new BigDecimal("0.50"), school.getRechargePricePer10());
        assertEquals(new BigDecimal("0.60"), school.getStudentRechargePricePer10());
        assertEquals(new BigDecimal("0.50"), result.get("minimumStudentRechargePricePer10"));
    }

    @Test
    void lockedSchoolStateIsRevalidatedBeforeViewerWrites() {
        UserAccount viewer = user(1L, "SCHOOL_VIEWER", 10L, 0);
        School disabledSchool = school(10L);
        disabledSchool.setEnabled(false);
        when(users.selectById(1L)).thenReturn(viewer);
        when(schools.selectOne(any())).thenReturn(disabledSchool);
        when(users.selectOne(any())).thenReturn(viewer);
        AuthContext.setUserId(1L);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.updateRechargePrice(new SchoolService.PriceInput(new BigDecimal("0.50"))));

        assertEquals("学校或统计账号已停用", error.getMessage());
    }

    @Test
    void lockedViewerStateIsRevalidatedBeforeViewerWrites() {
        UserAccount disabledViewer = user(1L, "SCHOOL_VIEWER", 10L, 0);
        disabledViewer.setAccountEnabled(false);
        when(users.selectById(1L)).thenReturn(disabledViewer);
        when(schools.selectOne(any())).thenReturn(school(10L));
        when(users.selectOne(any())).thenReturn(disabledViewer);
        AuthContext.setUserId(1L);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.updateRechargePrice(new SchoolService.PriceInput(new BigDecimal("0.50"))));

        assertEquals("学校或统计账号已停用", error.getMessage());
    }

    @Test
    void adminMovingSchoolViewerRevokesItsExistingSessions() {
        UserAccount admin = user(99L, "ADMIN", 0L, 0);
        UserAccount viewer = user(3L, "SCHOOL_VIEWER", 10L, 0);
        School currentSchool = school(10L);
        School destinationSchool = school(11L);
        when(users.selectById(99L)).thenReturn(admin);
        when(users.selectById(3L)).thenReturn(viewer);
        when(schools.selectOne(any())).thenReturn(currentSchool, destinationSchool);
        when(users.selectOne(any())).thenReturn(viewer);
        when(users.update(isNull(), any())).thenReturn(1);
        AuthContext.setUserId(99L);

        service.updateViewer(3L, new SchoolService.ViewerUpdate(11L, null, null));

        assertEquals(11L, viewer.getSchoolId());
        verify(security).invalidateSessions(3L);
    }

    @Test
    void schoolViewerCanPseudoDeleteSameSchoolTestUserAndForfeitGiftBalance() {
        UserAccount viewer = user(1L, "SCHOOL_VIEWER", 10L, 0);
        UserAccount student = user(2L, "USER", 10L, 25);
        student.setPhone("13800000002");
        when(users.selectById(1L)).thenReturn(viewer);
        when(schools.selectOne(any())).thenReturn(school(10L));
        when(users.selectOne(any())).thenReturn(viewer, student);
        when(users.update(isNull(), any())).thenReturn(1);
        when(jdbc.queryForObject(contains("recharge_order"), eq(Long.class), eq(2L))).thenReturn(0L);
        AuthContext.setUserId(1L);

        service.deleteStudent(2L, new SchoolService.DeleteInput("清理测试账号", "secret12"));

        var lockOrder = inOrder(schools, users, jdbc);
        lockOrder.verify(schools).selectOne(any());
        lockOrder.verify(users, org.mockito.Mockito.times(2)).selectOne(any());
        lockOrder.verify(jdbc).queryForObject(contains("recharge_order"), eq(Long.class), eq(2L));

        assertNotNull(student.getDeletedAt());
        assertEquals(1L, student.getDeletedBy());
        assertEquals(0, student.getPoints());
        assertFalse(student.getAccountEnabled());
        assertTrue(student.getPhone().startsWith("deleted_"));
        verify(transactions).insert(any(PointTransaction.class));
        verify(security).invalidateSessions(2L);
    }

    @Test
    void crossSchoolAndAnyRechargeOrderBlockDeletion() {
        UserAccount viewer = user(1L, "SCHOOL_VIEWER", 10L, 0);
        UserAccount otherSchool = user(2L, "USER", 11L, 0);
        when(users.selectById(1L)).thenReturn(viewer);
        when(schools.selectOne(any())).thenReturn(school(10L));
        when(users.selectOne(any())).thenReturn(viewer, otherSchool, viewer, otherSchool);
        AuthContext.setUserId(1L);

        ResponseStatusException crossSchool = assertThrows(ResponseStatusException.class,
                () -> service.deleteStudent(2L, null));
        assertEquals(HttpStatus.FORBIDDEN.value(), crossSchool.getStatusCode().value());

        otherSchool.setSchoolId(10L);
        when(jdbc.queryForObject(contains("recharge_order"), eq(Long.class), eq(2L))).thenReturn(1L);
        ResponseStatusException ordered = assertThrows(ResponseStatusException.class,
                () -> service.deleteStudent(2L, new SchoolService.DeleteInput(null, "secret12")));
        assertEquals(HttpStatus.CONFLICT.value(), ordered.getStatusCode().value());
    }

    @Test
    void schoolViewerMustKnowTargetTestAccountPasswordBeforeDeletion() {
        UserAccount viewer = user(1L, "SCHOOL_VIEWER", 10L, 0);
        UserAccount student = user(2L, "USER", 10L, 0);
        when(users.selectById(1L)).thenReturn(viewer);
        when(schools.selectOne(any())).thenReturn(school(10L));
        when(users.selectOne(any())).thenReturn(viewer, student);
        AuthContext.setUserId(1L);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.deleteStudent(2L, new SchoolService.DeleteInput(null, "wrong-secret")));

        assertEquals("测试账号密码验证失败", error.getMessage());
    }

    @Test
    void adminCanPseudoDeleteEmptySchoolAndItsViewer() {
        UserAccount admin = user(99L, "ADMIN", 0L, 0);
        UserAccount viewer = user(3L, "SCHOOL_VIEWER", 10L, 12);
        viewer.setPhone("13800000003");
        School school = school(10L);
        when(users.selectById(99L)).thenReturn(admin);
        when(schools.selectOne(any())).thenReturn(school);
        when(jdbc.queryForObject(contains("role='USER'"), eq(Long.class), eq(10L))).thenReturn(0L);
        when(jdbc.queryForObject(contains("recharge_order"), eq(Long.class), eq(10L), eq(10L))).thenReturn(0L);
        when(users.selectList(any())).thenReturn(List.of(viewer));
        when(users.update(isNull(), any())).thenReturn(1);
        when(schools.update(isNull(), any())).thenReturn(1);
        AuthContext.setUserId(99L);

        service.deleteSchool(10L, new SchoolService.DeleteInput("清理测试学校"));

        assertNotNull(school.getDeletedAt());
        assertFalse(school.getEnabled());
        assertEquals("~deleted:a", school.getSchoolCode());
        assertNotNull(viewer.getDeletedAt());
        assertEquals(0, viewer.getPoints());
        verify(security).invalidateSessions(3L);
    }

    @Test
    void activeDeletedUnderscoreCodeCannotCollideWithReleasedSchoolCode() {
        UserAccount admin = user(99L, "ADMIN", 0L, 0);
        School target = school(1L);
        when(users.selectById(99L)).thenReturn(admin);
        when(schools.selectOne(any())).thenReturn(target);
        when(jdbc.queryForObject(contains("role='USER'"), eq(Long.class), eq(1L))).thenReturn(0L);
        when(jdbc.queryForObject(contains("recharge_order"), eq(Long.class), eq(1L), eq(1L))).thenReturn(0L);
        when(users.selectList(any())).thenReturn(List.of());
        when(schools.update(isNull(), any())).thenReturn(1);
        AuthContext.setUserId(99L);

        service.deleteSchool(1L, null);

        assertEquals("~deleted:1", target.getSchoolCode());
        assertFalse("deleted_1".equals(target.getSchoolCode()));
    }

    @Test
    void schoolWithActiveUserCannotBeDeleted() {
        UserAccount admin = user(99L, "ADMIN", 0L, 0);
        when(users.selectById(99L)).thenReturn(admin);
        when(schools.selectOne(any())).thenReturn(school(10L));
        when(jdbc.queryForObject(contains("role='USER'"), eq(Long.class), eq(10L))).thenReturn(1L);
        AuthContext.setUserId(99L);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.deleteSchool(10L, null));
        assertEquals(HttpStatus.CONFLICT.value(), error.getStatusCode().value());
    }

    @Test
    void emptySchoolWithAnyLegacyOrCurrentOrderCannotBeDeleted() {
        UserAccount admin = user(99L, "ADMIN", 0L, 0);
        when(users.selectById(99L)).thenReturn(admin);
        when(schools.selectOne(any())).thenReturn(school(10L));
        when(jdbc.queryForObject(contains("role='USER'"), eq(Long.class), eq(10L))).thenReturn(0L);
        when(jdbc.queryForObject(contains("recharge_order"), eq(Long.class), eq(10L), eq(10L))).thenReturn(1L);
        AuthContext.setUserId(99L);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.deleteSchool(10L, null));

        assertEquals(HttpStatus.CONFLICT.value(), error.getStatusCode().value());
    }

    private UserAccount user(Long id, String role, Long schoolId, int points) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setRole(role);
        user.setSchoolId(schoolId);
        user.setAccountEnabled(true);
        user.setPasswordHash(TEST_PASSWORD_HASH);
        user.setPoints(points);
        user.setTotalPoints(points);
        user.setUsedPoints(0);
        return user;
    }

    private School school(Long id) {
        School school = new School();
        school.setId(id);
        school.setSchoolCode("TEST" + id);
        school.setSchoolName("测试学校");
        school.setEnabled(true);
        school.setRechargePricePer10(new BigDecimal("0.30"));
        school.setStudentRechargePricePer10(new BigDecimal("0.30"));
        return school;
    }
}
