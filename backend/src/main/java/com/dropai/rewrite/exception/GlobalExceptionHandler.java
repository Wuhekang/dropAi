package com.dropai.rewrite.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception,
                                                               HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("请求参数校验失败");
        return buildResponse(HttpStatus.BAD_REQUEST, message, request, exception);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception exception, HttpServletRequest request) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = "服务器内部错误";
        }
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, message, request, exception);
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message,
                                                              HttpServletRequest request, Exception exception) {
        String traceId = UUID.randomUUID().toString();
        String path = request.getRequestURI();
        log.error("API request failed, traceId={}, path={}, status={}, message={}",
                traceId, path, status.value(), message, exception);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", status.value());
        body.put("message", message);
        body.put("path", path);
        body.put("traceId", traceId);
        return ResponseEntity.status(status).body(body);
    }
}
