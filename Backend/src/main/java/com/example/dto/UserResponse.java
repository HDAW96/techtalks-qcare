package com.example.dto;

import com.example.entity.Patient;
import com.example.entity.User;

/**
 * The signed-in person as the frontend sees them. Deliberately not the User
 * entity: that carries the password hash, which must never leave the server.
 */
public class UserResponse {

    private Integer userId;
    private String fullName;
    private String phoneNumber;
    private String role;
    private Integer patientId;
    /** True for the seeded staff accounts until they choose their own password. */
    private boolean mustChangePassword;

    public UserResponse(Integer userId, String fullName, String phoneNumber, String role) {
        this.userId = userId;
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.role = role; 

    }

    public UserResponse() {
        //TODO Auto-generated constructor stub
    }

    public static UserResponse of(User user, Patient patient) {
        UserResponse dto = new UserResponse();
        dto.userId = user.getUserId();
        dto.fullName = user.getFullName();
        dto.phoneNumber = user.getPhoneNumber();
        dto.role = user.getRole();
        dto.mustChangePassword = Boolean.TRUE.equals(user.getMustChangePassword());
        if (patient != null) {
            dto.patientId = patient.getPatientId();
        }
        return dto;
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

    public Integer getPatientId() {
        return patientId;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

}
