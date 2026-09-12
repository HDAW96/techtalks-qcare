package com.example.dto;

/**
 * What the registration form sends.
 *
 * There is deliberately no role field: a caller must never be able to ask for
 * an account that is a doctor or a receptionist. The server decides the role.
 *
 * Setters matter here - Spring fills these from the request body through them,
 * so a DTO without setters silently receives nothing.
 */
public class RegisterDTO {

    private String fullName;
    private String phoneNumber;
    private String password;

    public RegisterDTO() {
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
