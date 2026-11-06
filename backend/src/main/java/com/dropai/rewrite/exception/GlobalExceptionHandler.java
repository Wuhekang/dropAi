package com.dropai.rewrite.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

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

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleUploadTooLarge(MaxUploadSizeExceededException exception,
                                                                    HttpServletRequest request) {
        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE,
                "上传文件或请求总大小超过服务器限制，请缩小文件后重试",
                request,
                exception);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException exception,
                                                                    HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String message = exception.getReason() == null || exception.getReason().isBlank()
                ? status.getReasonPhrase() : exception.getReason();
        return buildResponse(status, message, request, exception);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException exception,
                                                                 HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, message(exception, "请求参数不正确"), request, exception);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException exception,
                                                               HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, message(exception, "当前状态不允许此操作"), request, exception);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误", request, exception);
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

    private String message(Exception exception, String fallback) {
        return exception.getMessage() == null || exception.getMessage().isBlank() ? fallback : exception.getMessage();
    }
}
