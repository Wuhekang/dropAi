package com.dropai.rewrite;

import com.dropai.rewrite.entity.UserAccount;
import com.dropai.rewrite.mapper.UserAccountMapper;
import com.dropai.rewrite.mapper.UserSessionMapper;
import com.dropai.rewrite.service.AccountSecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountSecurityServiceTest {
    @Mock private UserAccountMapper accountMapper;
    @Mock private UserSessionMapper sessionMapper;
    private AccountSecurityService service;

    @BeforeEach
    void setUp() {
        service = new AccountSecurityService(accountMapper, sessionMapper);
    }

    @Test
    void ownPasswordChangeVerifiesCurrentPasswordAndInvalidatesEverySession() {
        UserAccount account = account("old-secret");
        when(accountMapper.selectActiveByIdForUpdate(account.getId())).thenReturn(account);
        when(accountMapper.updatePasswordHash(eq(account.getId()), anyString())).thenReturn(1);

        service.changeOwnPassword(account.getId(), "old-secret", "new-secret");

        assertTrue(new BCryptPasswordEncoder().matches("new-secret", account.getPasswordHash()));
        verify(accountMapper).updatePasswordHash(eq(account.getId()), anyString());
        verify(sessionMapper).delete(any());
    }

    @Test
    void wrongCurrentPasswordDoesNotChangeHashOrSessions() {
        UserAccount account = account("old-secret");
        String originalHash = account.getPasswordHash();
        when(accountMapper.selectActiveByIdForUpdate(account.getId())).thenReturn(account);

        assertThrows(IllegalArgumentException.class,
                () -> service.changeOwnPassword(account.getId(), "wrong-secret", "new-secret"));

        assertTrue(new BCryptPasswordEncoder().matches("old-secret", originalHash));
        verify(accountMapper, never()).updatePasswordHash(any(), anyString());
        verify(sessionMapper, never()).delete(any());
    }

    @Test
    void oversizedCurrentPasswordIsRejectedBeforeHashVerification() {
        UserAccount account = account("old-secret");
        when(accountMapper.selectActiveByIdForUpdate(account.getId())).thenReturn(account);

        assertThrows(IllegalArgumentException.class,
                () -> service.changeOwnPassword(account.getId(), "x".repeat(1000), "new-secret"));

        verify(accountMapper, never()).updatePasswordHash(any(), anyString());
        verify(sessionMapper, never()).delete(any());
    }

    @Test
    void resetRejectsInvalidPasswordBeforeWriting() {
        UserAccount account = account("old-secret");

        assertThrows(IllegalArgumentException.class, () -> service.resetPassword(account, "short"));

        verify(accountMapper, never()).updatePasswordHash(any(), anyString());
        verify(sessionMapper, never()).delete(any());
    }

    @Test
    void secondSelfChangeWithOldPasswordFailsAfterFirstLockedChange() {
        UserAccount account = account("old-secret");
        when(accountMapper.selectActiveByIdForUpdate(account.getId())).thenReturn(account);
        when(accountMapper.updatePasswordHash(eq(account.getId()), anyString())).thenReturn(1);

        service.changeOwnPassword(account.getId(), "old-secret", "first-new-secret");
        assertThrows(IllegalArgumentException.class,
                () -> service.changeOwnPassword(account.getId(), "old-secret", "second-new-secret"));

        assertTrue(new BCryptPasswordEncoder().matches("first-new-secret", account.getPasswordHash()));
        verify(accountMapper, org.mockito.Mockito.times(2)).selectActiveByIdForUpdate(account.getId());
        verify(accountMapper).updatePasswordHash(eq(account.getId()), anyString());
        verify(sessionMapper).delete(any());
    }

    private UserAccount account(String password) {
        UserAccount account = new UserAccount();
        account.setId(7L);
        account.setPasswordHash(new BCryptPasswordEncoder().encode(password));
        return account;
    }
}
