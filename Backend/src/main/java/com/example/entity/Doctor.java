package com.example.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "DOCTOR")
public class Doctor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "doctor_id")
    private Integer doctorId;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    private String specialization;

    public Doctor() {
    }

    public Integer getDoctorId() {
        return doctorId;
    }

    public User getUser() {
        return user;
    }

    public String getSpecialization() {
        return specialization;
    }
}