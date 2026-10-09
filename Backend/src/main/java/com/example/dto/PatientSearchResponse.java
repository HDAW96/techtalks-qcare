package com.example.dto;

import com.example.entity.Patient;

public class PatientSearchResponse {

    private final Integer patientId;
    private final String fullName;
    private final String phoneNumber;

    public PatientSearchResponse(Integer patientId, String fullName, String phoneNumber) {
        this.patientId = patientId;
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
    }

    public static PatientSearchResponse of(Patient patient) {
        return new PatientSearchResponse(
                patient.getPatientId(), patient.getFullName(), patient.getPhoneNumber());
    }

    public Integer getPatientId() {
        return patientId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }
}
