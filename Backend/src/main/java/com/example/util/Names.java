package com.example.util;

public final class Names {

    private Names() {
    }

    public static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim().replaceAll("[ ]+", " ");
        return cleaned.isEmpty() ? null : cleaned;
    }
}
