package com.tzp.zjzx.common.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

@Service
public class PasswordService {

    private static final int MD5_HEX_LENGTH = 32;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public String encode(String rawPassword) {
        if (!StringUtils.hasText(rawPassword)) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        return passwordEncoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String encodedPassword) {
        if (!StringUtils.hasText(rawPassword) || !StringUtils.hasText(encodedPassword)) {
            return false;
        }
        if (isBcrypt(encodedPassword)) {
            try {
                return passwordEncoder.matches(rawPassword, encodedPassword);
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }
        if (isLegacyMd5(encodedPassword)) {
            String inputMd5 = DigestUtils.md5DigestAsHex(rawPassword.getBytes(StandardCharsets.UTF_8));
            return encodedPassword.equalsIgnoreCase(inputMd5);
        }
        return false;
    }

    public boolean needsUpgrade(String encodedPassword) {
        return isLegacyMd5(encodedPassword);
    }

    private boolean isBcrypt(String encodedPassword) {
        return encodedPassword.startsWith("$2a$")
                || encodedPassword.startsWith("$2b$")
                || encodedPassword.startsWith("$2y$");
    }

    private boolean isLegacyMd5(String encodedPassword) {
        return encodedPassword.length() == MD5_HEX_LENGTH && encodedPassword.matches("[0-9a-fA-F]{32}");
    }
}
