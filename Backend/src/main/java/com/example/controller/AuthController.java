package com.example.controller;

import com.example.dto.AuthResponse;
import com.example.dto.ChangePasswordDTO;
import com.example.dto.LoginDTO;
import com.example.dto.RegisterDTO;
import com.example.dto.UserResponse;
import com.example.service.AuthService;
import com.example.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterDTO details) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(details));
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginDTO credentials) {
        return authService.login(credentials);
    }

    /**
     * Who the caller is, according to their token. The frontend calls this on
     * load to restore a session after a refresh.
     */
    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return authService.currentUser(signedInUserId(authentication));
    }

    /** Signed-in only: the user id comes from the token, never the body. */
    @PostMapping("/change-password")
    public UserResponse changePassword(Authentication authentication,
            @RequestBody ChangePasswordDTO request) {
        return authService.changePassword(signedInUserId(authentication),
                request.getCurrentPassword(), request.getNewPassword());
    }

    private Integer signedInUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Integer userId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Please sign in again.");
        }
        return userId;
    }
}
