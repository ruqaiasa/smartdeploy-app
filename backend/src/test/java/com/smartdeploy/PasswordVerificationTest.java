package com.smartdeploy;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordVerificationTest {

    @Test
    void verifyPassword() {

        BCryptPasswordEncoder encoder =
                new BCryptPasswordEncoder();

        String storedHash =
                "$2a$10$vHKuHVuKPenLhH3BlYhYkenT7dTaoILLsxJ5RYTS7IlsJpaQNuJ8a";

        boolean matches = encoder.matches(
                "NewPassword456",
                storedHash
        );

        System.out.println("Password matches: " + matches);

        assertTrue(matches);
    }
}