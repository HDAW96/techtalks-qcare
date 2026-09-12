package com.example.dto;

import java.time.LocalDateTime;

public class BookingRequestDTO {
    private String fullName;
    private String phoneNumber;
    private Integer doctorId;
    private Integer serviceId; // Added for the new schema
    private LocalDateTime appointmentDatetime;

    public String getFullName() {
        return fullName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public Integer getDoctorId() {
        return doctorId;
    }

    public Integer getServiceId() {
        return serviceId;
    }

    public LocalDateTime getAppointmentDatetime() {
        return appointmentDatetime;
    }
}
