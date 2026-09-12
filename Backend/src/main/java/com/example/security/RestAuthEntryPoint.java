package com.example.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Spring Security answers an unauthenticated request with 403 by default.
 * The frontend treats only 401 as "your session ended, sign in again", so
 * without this an expired token shows a generic error and leaves the user
 * stuck on the page.
 *
 * 401 = we do not know who you are. 403 = we know, and you may not do this.
 */
@Component
public class RestAuthEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        write(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED",
                "Please sign in to continue.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                "You do not have access to this.");
    }

    /**
     * Written by hand rather than through Jackson: both values are fixed
     * strings defined above with no quotes or backslashes in them, so there is
     * nothing to escape. Anything caller-supplied must go through the JSON
     * library instead.
     */
    private void write(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = """
                {"message": "%s", "error": "%s"}""".formatted(message, code);
        response.getWriter().write(body);
    }
}
