package com.dropai.rewrite.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dropai.rewrite.entity.UserAccount;
import com.dropai.rewrite.mapper.UserAccountMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
@Profile({"demo", "aiven"})
public class AdminBootstrap implements ApplicationRunner {
    private static final String ADMIN_PHONE = "13800000000";
    private static final String ADMIN_PASSWORD_HASH = "$2a$10$Rb4nhRPFpP8vOkfF3YZGaOniq.NKq0d3Rux7rINikUj/wmfT/snRG";
    private final UserAccountMapper accountMapper;

    public AdminBootstrap(UserAccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        UserAccount account = accountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getPhone, ADMIN_PHONE));
        if (account == null) {
            account = new UserAccount();
            account.setPhone(ADMIN_PHONE);
            account.setPasswordHash(ADMIN_PASSWORD_HASH);
            account.setRole("ADMIN");
            account.setCreatedAt(LocalDateTime.now());
            account.setUpdatedAt(LocalDateTime.now());
            accountMapper.insert(account);
        } else {
            account.setPasswordHash(ADMIN_PASSWORD_HASH);
            account.setRole("ADMIN");
            account.setUpdatedAt(LocalDateTime.now());
            accountMapper.updateById(account);
        }
    }
}
