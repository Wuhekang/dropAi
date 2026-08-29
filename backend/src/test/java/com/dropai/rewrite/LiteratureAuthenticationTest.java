package com.dropai.rewrite;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.auth.AuthInterceptor;
import com.dropai.rewrite.config.CorsConfig;
import com.dropai.rewrite.entity.UserAccount;
import com.dropai.rewrite.mapper.UserAccountMapper;
import com.dropai.rewrite.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiteratureAuthenticationTest {
    @Mock
    private AuthService authService;

    @Mock
    private UserAccountMapper userAccountMapper;

    private ObjectMapper objectMapper;
    private MappedInterceptor literatureInterceptor;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        AuthInterceptor authInterceptor = new AuthInterceptor(authService, objectMapper, userAccountMapper);
        ExposedInterceptorRegistry registry = new ExposedInterceptorRegistry();
        new CorsConfig(authInterceptor).addInterceptors(registry);
        literatureInterceptor = registry.interceptors().stream()
                .filter(MappedInterceptor.class::isInstance)
                .map(MappedInterceptor.class::cast)
                .filter(interceptor -> interceptor.getInterceptor() == authInterceptor)
                .findFirst()
                .orElseThrow();
    }

    @AfterEach
    void clearContext() {
        AuthContext.clear();
    }

    @Test
    void authenticatedLiteratureSearchPopulatesTheUserContext() throws Exception {
        assertTrue(literatureInterceptor.matches("/api/literature/search", new AntPathMatcher()));
        UserAccount account = new UserAccount();
        account.setRole("USER");
        when(authService.authenticate("valid-token")).thenReturn(42L);
        when(userAccountMapper.selectById(42L)).thenReturn(account);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/literature/search");
        request.addHeader("Authorization", "Bearer valid-token");

        assertTrue(literatureInterceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertEquals(42L, AuthContext.requireUserId());
    }

    @Test
    void unauthenticatedLiteratureSearchIsRejected() throws Exception {
        assertTrue(literatureInterceptor.matches("/api/literature/search", new AntPathMatcher()));
        when(authService.authenticate("")).thenReturn(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(literatureInterceptor.preHandle(
                new MockHttpServletRequest("POST", "/api/literature/search"), response, new Object()));
        assertEquals(401, response.getStatus());
        assertEquals("请先登录", objectMapper.readTree(response.getContentAsString()).path("message").asText());
        verifyNoInteractions(userAccountMapper);
    }

    private static final class ExposedInterceptorRegistry extends InterceptorRegistry {
        private List<Object> interceptors() {
            return getInterceptors();
        }
    }
}
