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

    /** BOOKED, CHECKED_IN, COMPLETED, CANCELLED or MISSED -matches APPOINTMENT_STATUS in the frontend constants. */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "BOOKED";

    @Column(name = "clinical_notes", length = 2000)
    private String clinicalNotes;

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
    public String getClinicalNotes() {
    return clinicalNotes;
    } 

    public void setClinicalNotes(String clinicalNotes) {
    this.clinicalNotes = clinicalNotes;
    }
    
    public Integer getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(Integer appointmentId) {
        this.appointmentId = appointmentId;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public Doctor getDoctor() {
        return doctor;
    }

    public void setDoctor(Doctor doctor) {
        this.doctor = doctor;
    }

    public MedicalService getMedicalService() {
        return medicalService;
    }

    public void setMedicalService(MedicalService medicalService) {
        this.medicalService = medicalService;
    }

    public LocalDateTime getAppointmentDatetime() {
        return appointmentDatetime;
    }

    public void setAppointmentDatetime(LocalDateTime appointmentDatetime) {
        this.appointmentDatetime = appointmentDatetime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
