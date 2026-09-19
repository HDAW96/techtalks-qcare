package com.example.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthEntryPoint restAuthEntryPoint;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthEntryPoint restAuthEntryPoint) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.restAuthEntryPoint = restAuthEntryPoint;
    }

    /**
     * BCrypt deliberately takes a moment to compute, which is what makes
     * guessing passwords in bulk impractical.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // The browser sends a token, not a cookie, so there is no
                // cookie for a third-party site to abuse and CSRF protection
                // has nothing to protect.
                .csrf(csrf -> csrf.disable())
                // CORS is configured in WebConfig; this tells Spring Security
                // to honour it.
                .cors(cors -> {
                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Signing in and registering cannot require being signed in.
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        // Anyone may see which doctors and services exist -
                        // the booking screen shows these before sign-in.
                        .requestMatchers(HttpMethod.GET, "/api/doctors/**", "/api/services/**").permitAll()
                        // Browsers send an unauthenticated OPTIONS request
                        // before a cross-origin call.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Behind the clinic QR and on the waiting-room screen:
                        // public, and none of them carries a name. These must
                        // come before the desk rule below, because the first
                        // rule that matches wins.
                        .requestMatchers(HttpMethod.GET, "/api/queues/join-options",
                                "/api/queues/today/board", "/api/tickets/by-code/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/queues/join").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/tickets/me").hasRole("PATIENT")
                        // Every other queue endpoint answers with patients' names
                        // and phone numbers, so being signed in is not enough -
                        // only the front desk may read or move the queue.
                        .requestMatchers("/api/queues/**").hasRole("RECEPTIONIST")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthEntryPoint)
                        .accessDeniedHandler(restAuthEntryPoint))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
