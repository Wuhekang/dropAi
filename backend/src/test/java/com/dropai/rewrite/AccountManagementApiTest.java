package com.dropai.rewrite;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.auth.AuthInterceptor;
import com.dropai.rewrite.controller.AccountController;
import com.dropai.rewrite.controller.AdminUserController;
import com.dropai.rewrite.entity.School;
import com.dropai.rewrite.entity.UserAccount;
import com.dropai.rewrite.mapper.PointTransactionMapper;
import com.dropai.rewrite.mapper.RechargeOrderMapper;
import com.dropai.rewrite.mapper.SchoolMapper;
import com.dropai.rewrite.mapper.UserAccountMapper;
import com.dropai.rewrite.service.AccountSecurityService;
import com.dropai.rewrite.service.AuthService;
import com.dropai.rewrite.vo.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountManagementApiTest {
    @Mock private UserAccountMapper userMapper;
    @Mock private PointTransactionMapper transactionMapper;
    @Mock private RechargeOrderMapper orderMapper;
    @Mock private SchoolMapper schoolMapper;
    @Mock private JdbcTemplate jdbc;
    @Mock private AccountSecurityService securityService;
    @Mock private AuthService authService;

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void accountControllerDelegatesSelfPasswordChangeForAuthenticatedAccount() {
        AuthContext.setUserId(42L);
        AccountController controller = new AccountController(securityService);

        Result<Boolean> result = controller.changePassword(
                new AccountController.PasswordChange("old-secret", "new-secret"));

        assertEquals(Boolean.TRUE, result.getData());
        verify(securityService).changeOwnPassword(42L, "old-secret", "new-secret");
    }

    @Test
    void schoolViewerCanReachAccountSecurityButStillCannotReachAdminApi() throws Exception {
        UserAccount viewer = account(7L, "SCHOOL_VIEWER");
        when(authService.authenticate("viewer-token")).thenReturn(7L);
        when(userMapper.selectById(7L)).thenReturn(viewer);
        AuthInterceptor interceptor = new AuthInterceptor(authService, new ObjectMapper(), userMapper);

        MockHttpServletRequest accountRequest = request("/api/account/password", "viewer-token");
        MockHttpServletResponse accountResponse = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(accountRequest, accountResponse, new Object()));
        interceptor.afterCompletion(accountRequest, accountResponse, new Object(), null);

        MockHttpServletRequest adminRequest = request("/api/admin/users", "viewer-token");
        MockHttpServletResponse adminResponse = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(adminRequest, adminResponse, new Object()));
        assertEquals(HttpStatus.FORBIDDEN.value(), adminResponse.getStatus());
    }

    @Test
    void adminCanResetUserOrSchoolViewerPasswordAndSessionsAreHandledBySecurityService() {
        AdminUserController controller = controller();
        UserAccount admin = account(1L, "ADMIN");
        UserAccount target = account(2L, "SCHOOL_VIEWER");
        when(userMapper.selectOne(any())).thenReturn(admin, target);
        AuthContext.setUserId(admin.getId());

        Result<Boolean> result = controller.resetPassword(target.getId(),
                new AdminUserController.PasswordReset("reset-secret"));

        assertEquals(Boolean.TRUE, result.getData());
        verify(securityService).resetPassword(target, "reset-secret");
    }

    @Test
    void nonAdminCannotResetPasswordAndAdminTargetsCannotBeReset() {
        AdminUserController controller = controller();
        UserAccount ordinary = account(1L, "USER");
        when(userMapper.selectOne(any())).thenReturn(ordinary);
        AuthContext.setUserId(ordinary.getId());

        ResponseStatusException forbidden = assertThrows(ResponseStatusException.class,
                () -> controller.resetPassword(2L, new AdminUserController.PasswordReset("reset-secret")));
        assertEquals(HttpStatus.FORBIDDEN.value(), forbidden.getStatusCode().value());
        verify(securityService, never()).resetPassword(any(), any());

        UserAccount admin = account(3L, "ADMIN");
        UserAccount otherAdmin = account(4L, "ADMIN");
        when(userMapper.selectOne(any())).thenReturn(admin, otherAdmin);
        AuthContext.setUserId(admin.getId());
        ResponseStatusException invalidTarget = assertThrows(ResponseStatusException.class,
                () -> controller.resetPassword(otherAdmin.getId(),
                        new AdminUserController.PasswordReset("reset-secret")));
        assertEquals(HttpStatus.BAD_REQUEST.value(), invalidTarget.getStatusCode().value());
    }

    @Test
    void adminCanOnlyReassignOrdinaryUserAndReassignmentRevokesSessions() {
        AdminUserController controller = controller();
        UserAccount admin = account(1L, "ADMIN");
        UserAccount target = account(2L, "USER");
        School school = new School();
        school.setId(11L);
        school.setEnabled(true);
        when(userMapper.selectOne(any())).thenReturn(admin, target);
        when(schoolMapper.selectOne(any())).thenReturn(school);
        when(userMapper.updateSchoolId(target.getId(), school.getId())).thenReturn(1);
        AuthContext.setUserId(admin.getId());

        Result<Boolean> result = controller.changeSchool(target.getId(),
                new AdminUserController.SchoolAssignment(school.getId()));

        assertEquals(Boolean.TRUE, result.getData());
        assertEquals(school.getId(), target.getSchoolId());
        verify(userMapper).updateSchoolId(target.getId(), school.getId());
        verify(securityService).invalidateSessions(target.getId());
    }

    @Test
    void schoolViewerCannotBeReassignedAsAnOrdinaryUser() {
        AdminUserController controller = controller();
        UserAccount admin = account(1L, "ADMIN");
        UserAccount viewer = account(2L, "SCHOOL_VIEWER");
        when(userMapper.selectOne(any())).thenReturn(admin, viewer);
        AuthContext.setUserId(admin.getId());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.changeSchool(viewer.getId(), new AdminUserController.SchoolAssignment(0L)));

        assertEquals(HttpStatus.BAD_REQUEST.value(), exception.getStatusCode().value());
        verify(userMapper, never()).updateSchoolId(any(), any());
        verify(securityService, never()).invalidateSessions(viewer.getId());
    }

    @Test
    void disabledSchoolCannotReceiveAUserAssignment() {
        AdminUserController controller = controller();
        UserAccount admin = account(1L, "ADMIN");
        UserAccount target = account(2L, "USER");
        School disabled = new School();
        disabled.setId(11L);
        disabled.setEnabled(false);
        when(userMapper.selectOne(any())).thenReturn(admin, target);
        when(schoolMapper.selectOne(any())).thenReturn(disabled);
        AuthContext.setUserId(admin.getId());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.changeSchool(target.getId(), new AdminUserController.SchoolAssignment(11L)));

        assertEquals(HttpStatus.CONFLICT.value(), exception.getStatusCode().value());
        verify(userMapper, never()).updateSchoolId(any(), any());
        verify(securityService, never()).invalidateSessions(target.getId());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void adminUserListExcludesDeletedRowsAtQueryLevelAndReflectsDisabledStatus() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "account-test");
        assistant.setCurrentNamespace("com.dropai.rewrite.AccountManagementApiTest");
        TableInfoHelper.initTableInfo(assistant, UserAccount.class);
        AdminUserController controller = controller();
        UserAccount admin = account(1L, "ADMIN");
        UserAccount disabled = account(2L, "USER");
        disabled.setPhone("13800000002");
        disabled.setAccountEnabled(false);
        when(userMapper.selectOne(any())).thenReturn(admin);
        when(userMapper.selectList(any())).thenReturn(List.of(disabled));
        AuthContext.setUserId(admin.getId());

        Result<List<Map<String, Object>>> result = controller.users(null, null);

        assertEquals("DISABLED", result.getData().get(0).get("status"));
        assertEquals(Boolean.FALSE, result.getData().get(0).get("accountEnabled"));
        ArgumentCaptor<LambdaQueryWrapper> wrapper = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(userMapper).selectList(wrapper.capture());
        assertFalse(wrapper.getValue().getExpression().getNormal().isEmpty());
    }

    private AdminUserController controller() {
        return new AdminUserController(userMapper, transactionMapper, orderMapper,
                schoolMapper, jdbc, securityService);
    }

    private UserAccount account(Long id, String role) {
        UserAccount account = new UserAccount();
        account.setId(id);
        account.setPhone("13800000001");
        account.setRole(role);
        account.setSchoolId(0L);
        account.setAccountEnabled(true);
        account.setPoints(0);
        account.setTotalPoints(0);
        account.setUsedPoints(0);
        return account;
    }

    private MockHttpServletRequest request(String uri, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
