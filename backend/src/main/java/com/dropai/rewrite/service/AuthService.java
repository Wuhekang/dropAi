package com.dropai.rewrite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dropai.rewrite.dto.PhoneAuthDTO;
import com.dropai.rewrite.entity.UserAccount;
import com.dropai.rewrite.entity.UserSession;
import com.dropai.rewrite.entity.School;
import com.dropai.rewrite.mapper.SchoolMapper;
import com.dropai.rewrite.mapper.UserAccountMapper;
import com.dropai.rewrite.mapper.UserSessionMapper;
import com.dropai.rewrite.vo.AuthVO;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {
    private final UserAccountMapper accountMapper;
    private final UserSessionMapper sessionMapper;
    private final SchoolMapper schoolMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(UserAccountMapper accountMapper, UserSessionMapper sessionMapper, SchoolMapper schoolMapper) {
        this.accountMapper = accountMapper;
        this.sessionMapper = sessionMapper;
        this.schoolMapper = schoolMapper;
    }

    @Transactional
    public AuthVO register(PhoneAuthDTO dto) {
        if (findByPhone(dto.getPhone()) != null) throw new IllegalArgumentException("该手机号已注册");
        UserAccount account = new UserAccount();
        account.setPhone(dto.getPhone());
        account.setPasswordHash(encoder.encode(dto.getPassword()));
        account.setRole("USER");
        account.setSchoolId(resolveRegistrationSchool(dto.getCollege()));
        account.setAccountEnabled(true);
        account.setPoints(0);
        account.setTotalPoints(0);
        account.setUsedPoints(0);
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        accountMapper.insert(account);
        return createSession(account);
    }

    @Transactional
    public AuthVO login(PhoneAuthDTO dto) {
        UserAccount account = accountMapper.selectActiveByPhoneForUpdate(dto.getPhone());
        if (account == null || !encoder.matches(dto.getPassword(), account.getPasswordHash())) {
            throw new IllegalArgumentException("手机号或密码错误");
        }
        validateAccount(account);
        return createSession(account);
    }

    public Long authenticate(String token) {
        if (token == null || token.isBlank()) return null;
        UserSession session = sessionMapper.selectById(token);
        if (session == null || session.getExpiresAt().isBefore(LocalDateTime.now())) return null;
        UserAccount account = accountMapper.selectById(session.getUserId());
        if (account == null || account.getPhone() == null || account.getPhone().isBlank()) return null;
        try { validateAccount(account); } catch (RuntimeException ignored) { return null; }
        return session.getUserId();
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) sessionMapper.deleteById(token);
    }

    private UserAccount findByPhone(String phone) {
        return accountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getPhone, phone)
                .isNull(UserAccount::getDeletedAt));
    }

    private AuthVO createSession(UserAccount account) {
        UserSession session = new UserSession();
        session.setToken(UUID.randomUUID().toString().replace("-", ""));
        session.setUserId(account.getId());
        session.setCreatedAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusDays(30));
        sessionMapper.insert(session);
        School school = account.getSchoolId() == null || account.getSchoolId() == 0 ? null : schoolMapper.selectOne(
                new LambdaQueryWrapper<School>().eq(School::getId, account.getSchoolId()).isNull(School::getDeletedAt));
        return new AuthVO(account.getId(), mask(account.getPhone()), session.getToken(), account.getRole(),
                account.getSchoolId() == null ? 0L : account.getSchoolId(), school == null ? null : school.getSchoolCode(),
                school == null ? null : school.getSchoolName());
    }

    private Long resolveRegistrationSchool(String code) {
        if (code == null || code.isBlank()) return 0L;
        School school = schoolMapper.selectOne(new LambdaQueryWrapper<School>()
                .eq(School::getSchoolCode, code.trim())
                .isNull(School::getDeletedAt)
                .last("FOR UPDATE"));
        if (school == null || school.getDeletedAt() != null)
            throw new IllegalArgumentException("学校专属注册链接无效或已失效");
        if (!Boolean.TRUE.equals(school.getEnabled())) throw new IllegalArgumentException("该学校已停用，暂时不能通过此链接注册");
        return school.getId();
    }

    private void validateAccount(UserAccount account) {
        if (account.getDeletedAt() != null) throw new IllegalStateException("账号已删除");
        if (Boolean.FALSE.equals(account.getAccountEnabled())) throw new IllegalStateException("账号已停用");
        if ("SCHOOL_VIEWER".equalsIgnoreCase(account.getRole())) {
            School school = account.getSchoolId() == null ? null : schoolMapper.selectById(account.getSchoolId());
            if (school == null || school.getDeletedAt() != null || !Boolean.TRUE.equals(school.getEnabled()))
                throw new IllegalStateException("学校或学校统计账号已停用");
        }
    }

    private String mask(String phone) {
        return phone == null || phone.length() < 11 ? "当前账号" : phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
