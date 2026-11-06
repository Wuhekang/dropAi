package com.dropai.rewrite.auth;

import com.dropai.rewrite.service.AuthService;
import com.dropai.rewrite.entity.UserAccount;
import com.dropai.rewrite.mapper.UserAccountMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.util.Map;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final AuthService authService;
    private final ObjectMapper objectMapper;
    private final UserAccountMapper userMapper;
    public AuthInterceptor(AuthService authService, ObjectMapper objectMapper, UserAccountMapper userMapper) {
        this.authService = authService;
        this.objectMapper = objectMapper;
        this.userMapper = userMapper;
    }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String header = request.getHeader("Authorization");
        Long userId = authService.authenticate(header != null && header.startsWith("Bearer ") ? header.substring(7) : "");
        if (userId != null) {
            UserAccount account = userMapper.selectById(userId);
            String path = request.getRequestURI();
            if (account != null && "SCHOOL_VIEWER".equalsIgnoreCase(account.getRole())
                    && !path.startsWith("/api/school-viewer/")
                    && !path.startsWith("/api/recharge/")
                    && !path.startsWith("/api/account/")) {
                response.setStatus(403); response.setContentType(MediaType.APPLICATION_JSON_VALUE); response.setCharacterEncoding("UTF-8");
                response.getWriter().write(objectMapper.writeValueAsString(Map.of("code",403,"message","学校统计账号只能访问本校只读统计")));
                return false;
            }
            AuthContext.setUserId(userId);
            return true;
        }
        response.setStatus(401);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of("code", 401, "message", "请先登录")));
        return false;
    }
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }
}
