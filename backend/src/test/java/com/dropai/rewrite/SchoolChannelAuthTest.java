package com.dropai.rewrite;

import com.dropai.rewrite.dto.PhoneAuthDTO;
import com.dropai.rewrite.entity.School;
import com.dropai.rewrite.entity.UserAccount;
import com.dropai.rewrite.mapper.SchoolMapper;
import com.dropai.rewrite.mapper.UserAccountMapper;
import com.dropai.rewrite.mapper.UserSessionMapper;
import com.dropai.rewrite.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchoolChannelAuthTest {
    @Mock UserAccountMapper accounts;
    @Mock UserSessionMapper sessions;
    @Mock SchoolMapper schools;

    @Test
    void ordinaryRegistrationIsUnbound() {
        when(accounts.selectOne(any())).thenReturn(null);
        assignGeneratedAccountId();

        var result = service().register(dto("13800000001", null));

        assertEquals(0L, result.schoolId());
        assertNull(result.schoolName());
        verify(schools, never()).selectOne(any());
    }

    @Test
    void validCollegeBindsByInternalId() {
        School school = school(9L, "GXDX2026", "广西大学", true, false);
        when(accounts.selectOne(any())).thenReturn(null);
        when(schools.selectOne(any())).thenReturn(school);
        assignGeneratedAccountId();

        var result = service().register(dto("13800000002", "GXDX2026"));

        assertEquals(9L, result.schoolId());
        assertEquals("广西大学", result.schoolName());
        assertTrue(result.userId() > 0);
    }

    @Test
    void invalidDisabledOrDeletedCollegeCannotBind() {
        when(accounts.selectOne(any())).thenReturn(null);
        when(schools.selectOne(any()))
                .thenReturn(null)
                .thenReturn(school(10L, "OFF", "停用学校", false, false))
                .thenReturn(school(11L, "DELETED", "已删除学校", false, true));
        AuthService auth = service();

        assertThrows(IllegalArgumentException.class,
                () -> auth.register(dto("13800000003", "UNKNOWN")));
        assertThrows(IllegalArgumentException.class,
                () -> auth.register(dto("13800000004", "OFF")));
        assertThrows(IllegalArgumentException.class,
                () -> auth.register(dto("13800000006", "DELETED")));
    }

    @Test
    void loginLinkCannotRebindExistingUser() {
        UserAccount account = new UserAccount();
        account.setId(17L);
        account.setPhone("13800000005");
        account.setPasswordHash(new BCryptPasswordEncoder().encode("secret12"));
        account.setRole("USER");
        account.setSchoolId(0L);
        account.setAccountEnabled(true);
        when(accounts.selectActiveByPhoneForUpdate("13800000005")).thenReturn(account);

        var result = service().login(dto("13800000005", "GXDX2026"));

        assertEquals(0L, result.schoolId());
        verify(schools, never()).selectOne(any());
    }

    private AuthService service() {
        return new AuthService(accounts, sessions, schools);
    }

    private void assignGeneratedAccountId() {
        doAnswer(invocation -> {
            UserAccount account = invocation.getArgument(0);
            account.setId(1L);
            return 1;
        }).when(accounts).insert(any(UserAccount.class));
    }

    private School school(Long id, String code, String name, boolean enabled, boolean deleted) {
        School school = new School();
        school.setId(id);
        school.setSchoolCode(code);
        school.setSchoolName(name);
        school.setEnabled(enabled);
        if (deleted) school.setDeletedAt(LocalDateTime.now());
        return school;
    }

    private PhoneAuthDTO dto(String phone, String college) {
        PhoneAuthDTO dto = new PhoneAuthDTO();
        dto.setPhone(phone);
        dto.setPassword("secret12");
        dto.setCollege(college);
        return dto;
    }
}
