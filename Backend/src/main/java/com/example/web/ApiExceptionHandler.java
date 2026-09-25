package com.example.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Turns exceptions into the shape the frontend expects: { message, error }.
 * client.js reads `message` to show the user and `error` to branch on.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> handleApiException(ApiException e) {
        return ResponseEntity.status(e.getStatus())
                .body(Map.of("message", e.getMessage(), "error", e.getCode()));
    }

    /**
     * A request with no body, or one that is not readable as JSON. Spring
     * raises this before any controller runs, so without this it fell to the
     * catch-all below and answered 500 - telling the caller the server broke
     * when in fact the request was wrong.
     *
     * The reason is deliberately not echoed back: a parser message names
     * fields and types, which is internal detail.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadableBody(
            HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "That request was not understood. Please try again.",
                        "error", "BODY_INVALID"));
    }

    /**
     * Anything unplanned. The real cause goes to the server log, not to the
     * browser - an internal message can leak how the system is built.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception e) {
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Something went wrong. Please try again.",
                        "error", "INTERNAL_ERROR"));
    }
}
