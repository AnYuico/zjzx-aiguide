package com.tzp.zjzx.common.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.util.StringUtils;

public class UserTokenFeignInterceptor implements RequestInterceptor {

    static final String USER_TOKEN_HEADER = "token";

    @Override
    public void apply(RequestTemplate requestTemplate) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes)) {
            return;
        }

        ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) requestAttributes;
        HttpServletRequest request = servletRequestAttributes.getRequest();
        String token = request.getHeader(USER_TOKEN_HEADER);
        if (!StringUtils.hasText(token)) {
            return;
        }

        requestTemplate.header(USER_TOKEN_HEADER, token);
    }
}
