package com.dropai.rewrite.service;

import com.dropai.rewrite.entity.UserAccount;
import com.dropai.rewrite.entity.UserSession;
import com.dropai.rewrite.mapper.UserAccountMapper;
import com.dropai.rewrite.mapper.UserSessionMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AccountSecurityService {
    private final UserAccountMapper accountMapper;
    private final UserSessionMapper sessionMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AccountSecurityService(UserAccountMapper accountMapper, UserSessionMapper sessionMapper) {
        this.accountMapper = accountMapper;
        this.sessionMapper = sessionMapper;
    }

    @Transactional
    public void changeOwnPassword(Long userId, String currentPassword, String newPassword) {
        UserAccount account = requireAccount(userId);
        if (currentPassword == null || currentPassword.length() < 6 || currentPassword.length() > 72
                || !encoder.matches(currentPassword, account.getPasswordHash())) {
            throw new IllegalArgumentException("当前密码错误");
        }
        validatePassword(newPassword);
        if (encoder.matches(newPassword, account.getPasswordHash())) {
            throw new IllegalArgumentException("新密码不能与当前密码相同");
        }
        resetPassword(account, newPassword);
    }

    @Transactional
    public void resetPassword(UserAccount account, String newPassword) {
        if (account == null || account.getId() == null || account.getDeletedAt() != null) {
            throw new IllegalArgumentException("用户不存在");
        }
        validatePassword(newPassword);
        String passwordHash = encoder.encode(newPassword);
        account.setPasswordHash(passwordHash);
        account.setUpdatedAt(LocalDateTime.now());
        if (accountMapper.updatePasswordHash(account.getId(), passwordHash) != 1) {
            throw new IllegalStateException("密码更新失败");
        }
        invalidateSessions(account.getId());
    }

    @Transactional
    public void invalidateSessions(Long userId) {
        if (userId == null) return;
        sessionMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getUserId, userId));
    }

    private UserAccount requireAccount(Long userId) {
        if (userId == null) throw new IllegalArgumentException("用户不存在");
        UserAccount account = accountMapper.selectActiveByIdForUpdate(userId);
        if (account == null) throw new IllegalArgumentException("用户不存在");
        return account;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 6 || password.length() > 72) {
            throw new IllegalArgumentException("密码长度为6-72位");
        }
    }
}
