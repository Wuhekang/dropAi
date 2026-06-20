package com.dropai.rewrite.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");

        if (isHtmlRequest(request)) {
            response.setHeader("Content-Type", "text/html; charset=utf-8");
            response.setHeader("Cache-Control", "no-cache");
        }

        filterChain.doFilter(request, response);
    }

    private boolean isHtmlRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || path.startsWith("/api/")) {
            return false;
        }
        if ("/".equals(path) || "/index.html".equals(path)) {
            return true;
        }
        return !path.substring(path.lastIndexOf('/') + 1).contains(".");
    }
}
