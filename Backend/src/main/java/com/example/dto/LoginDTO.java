package com.example.dto;

/**
 * Sign-in is by phone number + full name + password. The name is part of it
 * because a family shares one phone number, so the phone alone does not say
 * which of them is signing in.
 */
public class LoginDTO {

    private String phoneNumber;
    private String fullName;
    private String password;

    public LoginDTO() {
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
