package com.example.controller;

import java.util.List;
org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.dto.AdminUserResponse;
import com.example.dto.CreateUserDTO;
import com.example.dto.UserRequest;
import com.example.dto.UserResponse;
import com.example.service.UserService;

@RestController
@RequestMapping("/api/admin/users")
public class AdminController {

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<AdminUserResponse> searchUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String search
    ) {
        return userService.searchUsers(role, search);
    }

    @PostMapping
    public ResponseEntity<AdminUserResponse> createUser(@RequestBody CreateUserDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request));
    }

    @PutMapping("/{id}")
    public UserResponse updateUser(@PathVariable Integer id, @RequestBody UserRequest request) {
        return userService.updateUser(id, request);
    }
    @DeleteMapping("/{id}")
    public void deactivateUser(
      @PathVariable Integer id,
        Authentication authentication) {

    userService.deactivateUser(id, authentication);
}
}
