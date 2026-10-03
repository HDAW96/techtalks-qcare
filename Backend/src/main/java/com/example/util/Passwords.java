package com.example.util;

import org.springframework.http.HttpStatus;

import com.example.web.ApiException;

public final class Passwords {

    private Passwords() {
    }

    /**
     * The rules every new password must meet, on registering and on changing
     * it. The register and change-password forms check the same four rules
     * before submitting (frontend/src/password.js), so they never disagree.
     */
    public static void requireAcceptablePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_TOO_SHORT",
                    "Your password must be at least 8 characters.");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_NO_UPPERCASE",
                    "Your password must contain at least one uppercase letter.");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_NO_LOWERCASE",
                    "Your password must contain at least one lowercase letter.");
        }
        if (!password.matches(".*[^a-zA-Z0-9].*")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_NO_SPECIAL",
                    "Your password must contain at least one special character.");
        }
    }
}
