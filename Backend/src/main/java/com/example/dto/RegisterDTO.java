package com.example.dto;

public class RegisterDTO {
    private String fullName;
    private String phoneNumber;
    private Integer doctorId;
    private String email;
    private String password;
    private Integer serviceId; // Added for the new schema

    public RegisterDTO() {
    }
    
    public String getFullName() {
        return fullName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Integer getDoctorId() {
        return doctorId;
    }

    public Integer getServiceId() {
        return serviceId;
    }

}
