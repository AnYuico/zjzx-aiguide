package com.tzp.zjzx.common.security;

import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class InternalApiAuth {

    public static final String HEADER_NAME = "X-Internal-Token";

    private InternalApiAuth() {
    }

    public static void verify(String expectedToken, String actualToken) {
        if (!StringUtils.hasText(expectedToken) || !StringUtils.hasText(actualToken)
                || !MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                actualToken.getBytes(StandardCharsets.UTF_8))) {
            throw new MyException(ResultCodeEnum.LOGIN_AUTH);
        }
    }
}
