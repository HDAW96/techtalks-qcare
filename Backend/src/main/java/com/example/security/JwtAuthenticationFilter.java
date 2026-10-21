package com.example.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.repository.UserRepository;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Runs once per request. If the Authorization header carries a valid token,
 * the request is treated as coming from that signed-in user; otherwise it
 * continues as anonymous and the security rules decide whether that is allowed.
 *
 * An account still on a temporary password (must_change_password) is only
 * signed in for the two calls the change-password screen needs. Everywhere
 * else it continues as anonymous, so public pages still work, and any
 * endpoint that needs a signed-in user is refused by RestAuthEntryPoint with
 * 403 PASSWORD_CHANGE_REQUIRED - not 401, which the frontend reads as
 * "your session ended" and would sign the user out.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Set on a request refused only because its user must change their password. */
    public static final String PASSWORD_CHANGE_REQUIRED = "qcare.passwordChangeRequired";

    /** /auth/me restores the session after a reload; change-password is the way out. */
    private static final Set<String> ALLOWED_BEFORE_PASSWORD_CHANGE =
            Set.of("/api/auth/me", "/api/auth/change-password");

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }


    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Claims claims = jwtService.read(header.substring(7));
            
            if (claims != null) {
                Integer userId = Integer.valueOf(claims.getSubject());
                var user = userRepository.findById(userId);

                if (user.isPresent() && Boolean.TRUE.equals(user.get().getActive())
                        && Boolean.TRUE.equals(user.get().getMustChangePassword())
                        && !ALLOWED_BEFORE_PASSWORD_CHANGE.contains(pathOf(request))) {
                    request.setAttribute(PASSWORD_CHANGE_REQUIRED, Boolean.TRUE);
                } else if (user.isPresent() && Boolean.TRUE.equals(user.get().getActive())) {
                    String role = user.get().getRole();
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                    var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
                    
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    /** The path inside the application, without any context path or query. */
    private static String pathOf(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
