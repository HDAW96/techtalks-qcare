package com.example.dto;

import com.example.entity.User;

public class AdminUserResponse {

    private final Integer userId;
    private final String fullName;
    private final String phoneNumber;
    private final String role;
    /** False once the user has been removed: the account is kept, only switched off. */
    private final boolean active;

    public AdminUserResponse(Integer userId, String fullName, String phoneNumber, String role,
            boolean active) {
        this.userId = userId;
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.role = role;
        this.active = active;
    }

    public static AdminUserResponse of(User user) {
        return new AdminUserResponse(
                user.getUserId(), user.getFullName(), user.getPhoneNumber(), user.getRole(),
                Boolean.TRUE.equals(user.getActive()));
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

    public boolean isActive() {
        return active;
    }
}
