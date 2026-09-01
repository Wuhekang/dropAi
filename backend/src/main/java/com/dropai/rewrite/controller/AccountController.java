package com.dropai.rewrite.controller;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.service.AccountSecurityService;
import com.dropai.rewrite.vo.Result;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final AccountSecurityService accountSecurityService;

    public AccountController(AccountSecurityService accountSecurityService) {
        this.accountSecurityService = accountSecurityService;
    }

    @PutMapping("/password")
    public Result<Boolean> changePassword(@RequestBody PasswordChange request) {
        accountSecurityService.changeOwnPassword(AuthContext.requireUserId(),
                request == null ? null : request.currentPassword(),
                request == null ? null : request.newPassword());
        return Result.success(true);
    }

    public record PasswordChange(String currentPassword, String newPassword) {}
}
