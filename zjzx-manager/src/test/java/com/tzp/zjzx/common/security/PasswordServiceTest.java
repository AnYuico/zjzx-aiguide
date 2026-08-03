package com.tzp.zjzx.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordServiceTest {

    private final PasswordService passwordService = new PasswordService();

    @Test
    void bcryptPasswordsCanBeVerified() {
        String encoded = passwordService.encode("correct-password");

        assertTrue(passwordService.matches("correct-password", encoded));
        assertFalse(passwordService.matches("wrong-password", encoded));
        assertFalse(passwordService.needsUpgrade(encoded));
    }

    @Test
    void legacyMd5PasswordsRemainValidAndRequireUpgrade() {
        String encoded = DigestUtils.md5DigestAsHex(
                "correct-password".getBytes(StandardCharsets.UTF_8));

        assertTrue(passwordService.matches("correct-password", encoded));
        assertFalse(passwordService.matches("wrong-password", encoded));
        assertTrue(passwordService.needsUpgrade(encoded));
    }

    @Test
    void malformedStoredHashIsRejected() {
        assertFalse(passwordService.matches("password", "$2a$invalid"));
    }
}
