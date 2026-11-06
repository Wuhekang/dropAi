package com.dropai.rewrite;

import com.dropai.rewrite.dto.PhoneAuthDTO;
import com.dropai.rewrite.entity.UserAccount;
import com.dropai.rewrite.entity.UserSession;
import com.dropai.rewrite.mapper.SchoolMapper;
import com.dropai.rewrite.mapper.UserAccountMapper;
import com.dropai.rewrite.mapper.UserSessionMapper;
import com.dropai.rewrite.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthLoginLockingTest {
    @Test
    void loginUsesLockedCurrentPasswordAndOldPasswordCannotCreateSession() {
        UserAccountMapper accounts = mock(UserAccountMapper.class);
        UserSessionMapper sessions = mock(UserSessionMapper.class);
        UserAccount account = new UserAccount();
        account.setId(7L);
        account.setPhone("13800000007");
        account.setPasswordHash(new BCryptPasswordEncoder(4).encode("new-secret"));
        account.setRole("USER");
        account.setSchoolId(0L);
        account.setAccountEnabled(true);
        when(accounts.selectActiveByPhoneForUpdate("13800000007")).thenReturn(account);
        AuthService service = new AuthService(accounts, sessions, mock(SchoolMapper.class));

        PhoneAuthDTO oldPassword = dto("old-secret");
        assertThrows(IllegalArgumentException.class, () -> service.login(oldPassword));
        verify(accounts).selectActiveByPhoneForUpdate("13800000007");
        verify(sessions, never()).insert(any(UserSession.class));

        assertEquals(7L, service.login(dto("new-secret")).userId());
        verify(sessions).insert(any(UserSession.class));
    }

    private PhoneAuthDTO dto(String password) {
        PhoneAuthDTO dto = new PhoneAuthDTO();
        dto.setPhone("13800000007");
        dto.setPassword(password);
        return dto;
    }
}
