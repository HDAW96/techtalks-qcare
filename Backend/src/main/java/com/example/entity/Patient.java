package com.example.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A person treated at the clinic. A patient record can exist without an account:
 * a receptionist creates one for a walk-in, and the account is attached later if
 * that person registers.
 *
 * Identified by phone number + full name, because family members share a phone.
 */
@Entity
@Table(name = "patient", uniqueConstraints = {
        @UniqueConstraint(name = "uk_patient_phone_full_name", columnNames = { "phone_number", "full_name" })
})
public class Patient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "patient_id")
    private Integer patientId;

    /** Null for walk-ins registered at the desk who have never made an account. */
    @OneToOne
    @JoinColumn(name = "user_id", nullable = true, unique = true)
    private User user;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Patient() {
    }

    public Patient(String fullName, String phoneNumber) {
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.createdAt = LocalDateTime.now();
    }

    public Patient(String fullName, String phoneNumber, User user) {
        this(fullName, phoneNumber);
        this.user = user;
    }

    public Integer getPatientId() {
        return patientId;
    }

    public void setPatientId(Integer patientId) {
        this.patientId = patientId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /** The number a patient is given at the desk, e.g. P-00042. */
    public String getRecordNumber() {
        return patientId == null ? null : String.format("P-%05d", patientId);
    }
}
