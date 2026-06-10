package com.dropai.rewrite.controller;

import com.dropai.rewrite.service.AuthService;
import com.dropai.rewrite.vo.AuthVO;
import com.dropai.rewrite.vo.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    public AuthController(AuthService authService) { this.authService = authService; }

    @GetMapping("/wechat/status")
    public Result<Map<String, Boolean>> wechatStatus() {
        return Result.success(Map.of("configured", authService.wechatConfigured()));
    }
    @GetMapping("/wechat/url")
    public Result<Map<String, String>> wechatUrl() {
        return Result.success(Map.of("url", authService.wechatAuthorizeUrl()));
    }
    @GetMapping("/wechat/callback")
    public ResponseEntity<Void> wechatCallback(@RequestParam String code, @RequestParam String state) {
        AuthVO auth = authService.wechatCallback(code, state);
        return ResponseEntity.status(302).location(URI.create(authService.frontendSuccessUri(auth))).build();
    }
    @PostMapping("/logout")
    public Result<Boolean> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(token(authorization));
        return Result.success(true);
    }
    private String token(String value) { return value != null && value.startsWith("Bearer ") ? value.substring(7) : ""; }
}
