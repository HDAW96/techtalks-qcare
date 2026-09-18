package com.example.dto;

/**
 * What the change-password form sends. No user id: the server takes it from
 * the token, so nobody can change someone else's password.
 *
 * Setters matter here - Spring fills these from the request body through them,
 * so a DTO without setters silently receives nothing.
 */
public class ChangePasswordDTO {

    private String currentPassword;
    private String newPassword;

    public ChangePasswordDTO() {
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
