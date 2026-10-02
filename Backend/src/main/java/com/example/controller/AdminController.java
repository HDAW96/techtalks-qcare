package com.example.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.dto.AdminUserResponse;
import com.example.dto.CreateUserDTO;
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

    @PutMapping("/{id}")
    public ResponseEntity<AdminUserResponse> updateUser(
            @PathVariable Integer id,
            @RequestBody UserRequest request) {
        User updated = userService.updateUser(id, request);
        return ResponseEntity.ok(AdminUserResponse.of(updated));
    }
}
