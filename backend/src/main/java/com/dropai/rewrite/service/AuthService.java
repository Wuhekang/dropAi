package com.dropai.rewrite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dropai.rewrite.dto.AuthDTO;
import com.dropai.rewrite.entity.UserAccount;
import com.dropai.rewrite.entity.UserSession;
import com.dropai.rewrite.mapper.UserAccountMapper;
import com.dropai.rewrite.mapper.UserSessionMapper;
import com.dropai.rewrite.vo.AuthVO;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {
    private final UserAccountMapper accountMapper;
    private final UserSessionMapper sessionMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(UserAccountMapper accountMapper, UserSessionMapper sessionMapper) {
        this.accountMapper = accountMapper;
        this.sessionMapper = sessionMapper;
    }

    public AuthVO register(AuthDTO dto) {
        String username = dto.getUsername().trim().toLowerCase();
        if (findAccount(username) != null) {
            throw new IllegalArgumentException("账号已存在");
        }
        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setPasswordHash(encoder.encode(dto.getPassword()));
        account.setCreatedAt(LocalDateTime.now());
        accountMapper.insert(account);
        return createSession(account);
    }

    public AuthVO login(AuthDTO dto) {
        UserAccount account = findAccount(dto.getUsername().trim().toLowerCase());
        if (account == null || !encoder.matches(dto.getPassword(), account.getPasswordHash())) {
            throw new IllegalArgumentException("账号或密码错误");
        }
        return createSession(account);
    }

    public Long authenticate(String token) {
        if (token == null || token.isBlank()) return null;
        UserSession session = sessionMapper.selectById(token);
        if (session == null || session.getExpiresAt().isBefore(LocalDateTime.now())) return null;
        return session.getUserId();
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) sessionMapper.deleteById(token);
    }

    private UserAccount findAccount(String username) {
        return accountMapper.selectOne(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, username));
    }

    private AuthVO createSession(UserAccount account) {
        UserSession session = new UserSession();
        session.setToken(UUID.randomUUID().toString().replace("-", ""));
        session.setUserId(account.getId());
        session.setCreatedAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusDays(30));
        sessionMapper.insert(session);
        return new AuthVO(account.getId(), account.getUsername(), session.getToken());
    }
}
