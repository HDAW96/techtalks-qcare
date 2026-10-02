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

/**
 * Runs once per request. If the Authorization header carries a valid token,
 * the request is treated as coming from that signed-in user; otherwise it
 * continues as anonymous and the security rules decide whether that is allowed.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

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

            if (user.isPresent() && Boolean.TRUE.equals(user.get().getActive())) {

                String role = user.get().getRole();

                var authorities =
                        List.of(new SimpleGrantedAuthority("ROLE_" + role));

                var authentication =
                        new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                authorities);

                SecurityContextHolder.getContext()
                        .setAuthentication(authentication);
            }
        }
    }

    filterChain.doFilter(request, response);
}
}
