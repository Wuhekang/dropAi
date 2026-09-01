package com.dropai.rewrite;

import com.dropai.rewrite.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");

    @Test
    void responseStatusAndClientExceptionsKeepUsefulFourHundredStatusCodes() {
        var forbidden = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限"), request);
        var badRequest = handler.handleBadRequest(new IllegalArgumentException("参数错误"), request);
        var conflict = handler.handleConflict(new IllegalStateException("状态冲突"), request);

        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());
        assertEquals("无权限", forbidden.getBody().get("message"));
        assertEquals(HttpStatus.BAD_REQUEST, badRequest.getStatusCode());
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
    }

    @Test
    void internalErrorNeverLeaksExceptionMessage() {
        var response = handler.handleException(new RuntimeException("database-password=secret"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("服务器内部错误", response.getBody().get("message"));
        assertNotEquals("database-password=secret", response.getBody().get("message"));
    }
}
