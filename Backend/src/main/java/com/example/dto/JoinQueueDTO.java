package com.example.dto;

/**
 * What the join page sends after the patient scans the clinic QR.
 *
 * `date` and `sig` are the QR's own values, checked again here. `fullName`
 * and `phoneNumber` are only read when the caller is not signed in; a signed-in
 * patient always joins as themselves.
 *
 * Setters matter here - Spring fills these from the request body through them,
 * so a DTO without setters silently receives nothing.
 */
public class JoinQueueDTO {

    private String date;
    private String sig;
    private Integer doctorId;
    private Integer serviceId;
    private String fullName;
    private String phoneNumber;

    public JoinQueueDTO() {
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getSig() {
        return sig;
    }

    public void setSig(String sig) {
        this.sig = sig;
    }

    public Integer getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(Integer doctorId) {
        this.doctorId = doctorId;
    }

    public Integer getServiceId() {
        return serviceId;
    }

    public void setServiceId(Integer serviceId) {
        this.serviceId = serviceId;
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
}
