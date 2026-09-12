package com.example.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Issues and reads the signed token a browser sends back on every request.
 *
 * The token says who the user is and what role they have. It is signed, so it
 * cannot be edited by the browser, but it is NOT encrypted - anyone can read
 * its contents. Never put anything secret in it.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expiryMillis;

    public JwtService(
            @Value("${qcare.jwt.secret}") String secret,
            @Value("${qcare.jwt.expiry-hours:12}") long expiryHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryMillis = expiryHours * 60 * 60 * 1000;
    }

    public String issue(Integer userId, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiryMillis))
                .signWith(key)
                .compact();
    }

    /**
     * @return the claims if the token is genuine and unexpired, otherwise null
     */
    public Claims read(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            // Tampered, expired or malformed - all mean "not signed in".
            return null;
        }
    }
}
