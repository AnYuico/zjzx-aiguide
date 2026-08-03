package com.tzp.zjzx.common.feign;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class UserTokenFeignInterceptorTest {

    private final UserTokenFeignInterceptor interceptor = new UserTokenFeignInterceptor();

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldForwardTokenFromServletRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(UserTokenFeignInterceptor.USER_TOKEN_HEADER, "user-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RequestTemplate requestTemplate = new RequestTemplate();

        interceptor.apply(requestTemplate);

        Collection<String> values = requestTemplate.headers()
                .get(UserTokenFeignInterceptor.USER_TOKEN_HEADER);
        assertEquals(1, values.size());
        assertEquals("user-token", values.iterator().next());
    }

    @Test
    void shouldSkipHeaderWhenServletRequestHasNoToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RequestTemplate requestTemplate = new RequestTemplate();

        interceptor.apply(requestTemplate);

        assertFalse(requestTemplate.headers()
                .containsKey(UserTokenFeignInterceptor.USER_TOKEN_HEADER));
    }

    @Test
    void shouldSkipSafelyWithoutServletRequestContext() {
        RequestContextHolder.resetRequestAttributes();
        RequestTemplate requestTemplate = new RequestTemplate();

        assertDoesNotThrow(() -> interceptor.apply(requestTemplate));
        assertFalse(requestTemplate.headers()
                .containsKey(UserTokenFeignInterceptor.USER_TOKEN_HEADER));
    }
}
