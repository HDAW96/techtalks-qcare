package com.example.dto;

import com.example.entity.User;

public class AdminUserResponse {

    private final Integer userId;
    private final String fullName;
    private final String phoneNumber;
    private final String role;

    public AdminUserResponse(Integer userId, String fullName, String phoneNumber, String role) {
        this.userId = userId;
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.role = role;
    }

    public static AdminUserResponse of(User user) {
        return new AdminUserResponse(
                user.getUserId(), user.getFullName(), user.getPhoneNumber(), user.getRole());
    }

    public Integer getUserId() {
        return userId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getRole() {
        return role;
    }
}
