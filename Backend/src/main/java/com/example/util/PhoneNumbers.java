package com.example.util;

import java.util.regex.Pattern;

/**
 * Lebanese phone numbers arrive typed in several shapes - 03 123456,
 * +961 3 123456, 00961 3 123456. They are all the same person, so they are
 * stored in one shape: local format, no spaces, e.g. 03123456.
 *
 * The same rules live in the frontend so both sides agree on what a number is.
 */
public final class PhoneNumbers {

    private static final Pattern PHONE_SEARCH = Pattern.compile("[\\d\\s+()-]*\\d[\\d\\s+()-]*");

    private PhoneNumbers() {
    }

    /**
     * @return the number in local form, or null if it is blank
     */
    public static String normalise(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9+]", "");
        if (digits.isEmpty()) {
            return null;
        }
        if (digits.startsWith("+961")) {
            digits = digits.substring(4);
        } else if (digits.startsWith("00961")) {
            digits = digits.substring(5);
        } else if (digits.startsWith("961") && digits.length() > 9) {
            digits = digits.substring(3);
        }
        digits = digits.replace("+", "");
        // Local numbers are written with a leading zero; the international
        // form drops it, so put it back.
        if (!digits.startsWith("0")) {
            digits = "0" + digits;
        }
        return digits;
    }

    /**
     * Lebanese numbers are 7 or 8 digits after the leading zero:
     * mobile 03/70-81 + 6 digits, landline 01-09 + 6 digits.
     */
    public static boolean isValidLebanese(String normalised) {
        return normalised != null && normalised.matches("0[0-9]{7,8}");
    }

    /** Whether text looks like a phone number rather than a name. */
    public static boolean isPhoneSearch(String text) {
        return text != null && PHONE_SEARCH.matcher(text).matches();
    }

    /**
     * Strips common formatting and maps international prefixes to local form.
     * Unlike {@link #normalise(String)}, this preserves a partial number without
     * adding a leading zero, so searches such as "400 001" still match.
     */
    public static String searchFragment(String typed) {
        String digits = typed.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+961")) {
            return "0" + digits.substring(4);
        }
        if (digits.startsWith("00961")) {
            return "0" + digits.substring(5);
        }
        return digits.replace("+", "");
    }
}
