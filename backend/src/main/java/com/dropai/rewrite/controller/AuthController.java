package com.dropai.rewrite.controller;

import com.dropai.rewrite.dto.AuthDTO;
import com.dropai.rewrite.service.AuthService;
import com.dropai.rewrite.vo.AuthVO;
import com.dropai.rewrite.vo.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/register")
    public Result<AuthVO> register(@Valid @RequestBody AuthDTO dto) { return Result.success(authService.register(dto)); }
    @PostMapping("/login")
    public Result<AuthVO> login(@Valid @RequestBody AuthDTO dto) { return Result.success(authService.login(dto)); }
    @PostMapping("/logout")
    public Result<Boolean> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(token(authorization));
        return Result.success(true);
    }
    private String token(String value) { return value != null && value.startsWith("Bearer ") ? value.substring(7) : ""; }
}
