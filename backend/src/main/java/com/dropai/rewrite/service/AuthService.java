package com.dropai.rewrite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dropai.rewrite.dto.PhoneAuthDTO;
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

    public AuthVO register(PhoneAuthDTO dto) {
        if (findByPhone(dto.getPhone()) != null) throw new IllegalArgumentException("该手机号已注册");
        UserAccount account = new UserAccount();
        account.setPhone(dto.getPhone());
        account.setPasswordHash(encoder.encode(dto.getPassword()));
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        accountMapper.insert(account);
        return createSession(account);
    }

    public AuthVO login(PhoneAuthDTO dto) {
        UserAccount account = findByPhone(dto.getPhone());
        if (account == null || !encoder.matches(dto.getPassword(), account.getPasswordHash())) {
            throw new IllegalArgumentException("手机号或密码错误");
        }
        return createSession(account);
    }

    public Long authenticate(String token) {
        if (token == null || token.isBlank()) return null;
        UserSession session = sessionMapper.selectById(token);
        if (session == null || session.getExpiresAt().isBefore(LocalDateTime.now())) return null;
        UserAccount account = accountMapper.selectById(session.getUserId());
        return account == null || account.getPhone() == null || account.getPhone().isBlank() ? null : session.getUserId();
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) sessionMapper.deleteById(token);
    }
    private UserAccount findByPhone(String phone) {
        return accountMapper.selectOne(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getPhone, phone));
    }
    private AuthVO createSession(UserAccount account) {
        UserSession session = new UserSession();
        session.setToken(UUID.randomUUID().toString().replace("-", ""));
        session.setUserId(account.getId());
        session.setCreatedAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusDays(30));
        sessionMapper.insert(session);
        return new AuthVO(account.getId(), mask(account.getPhone()), session.getToken());
    }
    private String mask(String phone) { return phone.substring(0, 3) + "****" + phone.substring(7); }
}
