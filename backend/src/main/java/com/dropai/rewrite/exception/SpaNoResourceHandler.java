package com.dropai.rewrite.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SpaNoResourceHandler {

    private static final String[] BACKEND_PREFIXES = {
            "/api/",
            "/actuator/",
            "/uploads/",
            "/files/",
            "/download/",
            "/ws/",
            "/swagger-ui/",
            "/v3/api-docs/"
    };

    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNoResource(NoResourceFoundException exception, HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (isSpaRoute(request.getMethod(), uri)) {
            return "forward:/index.html";
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", HttpStatus.NOT_FOUND.value());
        body.put("message", exception.getMessage());
        body.put("path", uri);
        body.put("traceId", UUID.randomUUID().toString());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    private boolean isSpaRoute(String method, String uri) {
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            return false;
        }
        if (uri == null || uri.isBlank() || "/index.html".equals(uri)) {
            return false;
        }
        for (String prefix : BACKEND_PREFIXES) {
            if (uri.startsWith(prefix) || uri.equals(prefix.substring(0, prefix.length() - 1))) {
                return false;
            }
        }
        String lastSegment = uri.substring(uri.lastIndexOf('/') + 1);
        return !lastSegment.contains(".");
    }
}
