package com.example.dto;

import com.example.entity.Doctor;

/** A doctor as the booking screen needs them: who they are, nothing more. */
public class DoctorResponse {

    private Integer id;
    private String name;
    private String specialization;

    public DoctorResponse() {
    }

    public static DoctorResponse of(Doctor doctor) {
        DoctorResponse dto = new DoctorResponse();
        dto.id = doctor.getDoctorId();
        dto.name = doctor.getUser().getFullName();
        dto.specialization = doctor.getSpecialization();
        return dto;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSpecialization() {
        return specialization;
    }
}
