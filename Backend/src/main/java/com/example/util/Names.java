package com.example.util;

import com.example.web.ApiException;
import org.springframework.http.HttpStatus;

public final class Names {

    /** Well inside the database column, so a long name is refused, not a crash. */
    public static final int MAX_LENGTH = 100;

    private Names() {
    }

    /** A name longer than the limit is the caller's mistake: 400, not a database error. */
    public static void requireWithinLimit(String name) {
        if (name != null && name.length() > MAX_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NAME_TOO_LONG",
                    "The name can be at most " + MAX_LENGTH + " characters.");
        }
    }

    public static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim().replaceAll("[ ]+", " ");
        return cleaned.isEmpty() ? null : cleaned;
    }
}
