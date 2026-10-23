package com.example.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
     * An address no controller answers. Spring 6 reports this as an
     * exception rather than its own 404 page, so without this it fell to the
     * catch-all and a mistyped URL looked like a server crash.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handleNoSuchAddress(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "That address does not exist.",
                        "error", "NOT_FOUND"));
    }

    /** A path or query value of the wrong type, e.g. /api/doctors/abc. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleWrongType(
            MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Part of that request is not valid: " + e.getName() + ".",
                        "error", "PARAM_INVALID"));
    }

    /** A required query value left out, e.g. availability without serviceId. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> handleMissingValue(
            MissingServletRequestParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Part of that request is missing: " + e.getParameterName() + ".",
                        "error", "PARAM_MISSING"));
    }

    /** A real address called with the wrong method, e.g. GET on login. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handleWrongMethod(
            HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Map.of("message", "That action is not allowed here.",
                        "error", "METHOD_NOT_ALLOWED"));
    }

    /**
     * A body sent as something other than JSON (or with no content type at
     * all). Spring raises this before any controller runs, so without this it
     * fell to the catch-all and answered 500.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handleWrongContentType(
            HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(Map.of("message", "Please send that as JSON.",
                        "error", "UNSUPPORTED_MEDIA_TYPE"));
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
