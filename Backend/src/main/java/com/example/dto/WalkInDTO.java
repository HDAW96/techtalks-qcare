package com.example.dto;

/**
 * A person the receptionist adds at the desk. There is no account and no
 * appointment - a name, a number to call them back on, and what they came for.
 */
public class WalkInDTO {

    private String fullName;
    private String phoneNumber;
    private Integer serviceId;

    public WalkInDTO() {
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

    public Integer getServiceId() {
        return serviceId;
    }

    public void setServiceId(Integer serviceId) {
        this.serviceId = serviceId;
    }
}
