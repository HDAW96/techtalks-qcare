package com.example.service;

import org.springframework.stereotype.Service;

import com.example.dto.AdminUserResponse;
import com.example.entity.User;
import com.example.repository.UserRepository;
import java.util.List;

@Service 
public class UserService {
    private final UserRepository userRepository;
   

 public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

   public List<AdminUserResponse> searchUsers(String role, String search) {

    List<User> users = userRepository.searchUsers(role, search);

    return users.stream()
            .map(AdminUserResponse::of)
            .toList();
}
}
