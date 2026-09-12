package com.example.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "appointment")
public class Appointment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "appointment_id")
    private Integer appointmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private MedicalService medicalService;

    @Column(name = "appointment_datetime", nullable = false)
    private LocalDateTime appointmentDatetime;

    @Column(nullable = false)
    private String status = "BOOKED";

    public Appointment() {
    }

    public Appointment(Patient patient, Doctor doctor, LocalDateTime appointmentDatetime,
            MedicalService medicalService) {
        this.patient = patient;
        this.doctor = doctor;
        this.appointmentDatetime = appointmentDatetime;
        this.medicalService = medicalService;
        this.status = "BOOKED";
    }

    public Integer getAppointmentId() {
        return appointmentId;
    }

    public Patient getPatient() {
        return patient;
    }

    public Doctor getDoctor() {
        return doctor;
    }

    public LocalDateTime getAppointmentDatetime() {
        return appointmentDatetime;
    }

    public String getStatus() {
        return status;
    }
}