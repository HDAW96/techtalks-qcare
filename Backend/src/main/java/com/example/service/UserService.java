package com.example.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.dto.AdminUserResponse;
import com.example.dto.UserRequest;
import com.example.dto.UserResponse;
import com.example.entity.User;
import com.example.repository.UserRepository;
import com.example.web.ApiException;

import java.util.List;

@Service 
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;


 public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

   public List<AdminUserResponse> searchUsers(String role, String search) {

    List<User> users = userRepository.searchUsers(role, search);

    return users.stream()
            .map(AdminUserResponse::of)
            .toList();
}
     public UserResponse updateUser(Integer id, UserRequest request) {

    User user = userRepository.findById(id)
            .orElseThrow(() -> new ApiException(
                    HttpStatus.NOT_FOUND,
                    "USER_NOT_FOUND",
                    "User not found"
            ));

    if (request.getFullName() != null) {
        user.setFullName(request.getFullName());
    }

    if (request.getPhoneNumber() != null) {
        user.setPhoneNumber(request.getPhoneNumber());
    }

    if (request.getRole() != null) {
        user.setRole(request.getRole());
    }

    if (request.getPassword() != null && !request.getPassword().isBlank()) {
        user.setPasswordHash(
                passwordEncoder.encode(request.getPassword())
        );
    }

    userRepository.save(user);

    return new UserResponse(
            user.getUserId(),
            user.getFullName(),
            user.getPhoneNumber(),
            user.getRole()
    );
}
}
