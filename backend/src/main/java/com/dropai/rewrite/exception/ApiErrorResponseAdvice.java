package com.dropai.rewrite.exception;

import com.dropai.rewrite.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class ApiErrorResponseAdvice implements ResponseBodyAdvice<Object> {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorResponseAdvice.class);

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        String path = request.getURI().getPath();
        if (!path.startsWith("/api/") || !(body instanceof Result<?> result) || !isServerError(result.getCode())) {
            return body;
        }

        String traceId = UUID.randomUUID().toString();
        String message = result.getMessage();
        if (message == null || message.isBlank()) {
            message = "服务器内部错误";
        }

        log.error("API result failed, traceId={}, path={}, status=500, message={}", traceId, path, message);
        response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);

        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("code", 500);
        errorBody.put("message", message);
        errorBody.put("path", path);
        errorBody.put("traceId", traceId);
        return errorBody;
    }

    private boolean isServerError(Object code) {
        return Integer.valueOf(500).equals(code) || "500".equals(String.valueOf(code));
    }
}
