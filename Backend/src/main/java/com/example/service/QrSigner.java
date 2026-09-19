package com.example.service;

import com.example.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * The clinic's daily QR code (SCRUM-56 guide §5).
 *
 * A QR code is only text, so anyone can read one and anyone can print a new
 * one. The link it holds therefore carries a signature only the server can
 * make: an HMAC of the date under a secret key. Change the date and the
 * signature no longer matches; keep yesterday's photo and the date is no
 * longer today.
 */
@Component
public class QrSigner {

    /**
     * Says what the signature is for, so it can never be replayed as a
     * signature for anything else signed with the same key.
     */
    private static final String PURPOSE = "clinic-queue|";

    private final byte[] secret;
    private final String frontendUrl;

    public QrSigner(@Value("${qcare.qr.secret}") String secret,
            @Value("${qcare.frontend-url}") String frontendUrl) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.frontendUrl = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;
    }

    /** The link the desk shows as today's QR code. */
    public String joinUrl(LocalDate date) {
        return frontendUrl + "/join?date=" + date + "&sig=" + sign(date);
    }

    /**
     * Refuses a QR that was not made here (400 INVALID_QR) or not made for
     * today (400 EXPIRED_QR). Returns the date it was made for.
     */
    public LocalDate check(String date, String sig) {
        ApiException invalid = new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QR",
                "This QR code is not valid. Please scan the code shown at the clinic.");
        if (date == null || sig == null) {
            throw invalid;
        }
        LocalDate parsed;
        try {
            parsed = LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw invalid;
        }
        // MessageDigest.isEqual takes the same time however many characters
        // match, so the check itself gives nothing away about the signature.
        byte[] expected = sign(parsed).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, sig.getBytes(StandardCharsets.UTF_8))) {
            throw invalid;
        }
        if (!parsed.equals(LocalDate.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EXPIRED_QR",
                    "This QR code is from another day. Please scan today's code at the clinic.");
        }
        return parsed;
    }

    String sign(LocalDate date) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] raw = mac.doFinal((PURPOSE + date).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (GeneralSecurityException e) {
            // HmacSHA256 is required on every Java platform, so this means a
            // broken runtime rather than bad input.
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }
}
