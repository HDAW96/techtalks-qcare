package com.example.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * One block of days a doctor is away - vacation or sick leave - during
 * which the booking screen must stop offering any time that day, and a
 * booking or reschedule for that day must be refused.
 *
 * Not a row in DoctorAvailability: that table is the doctor's recurring
 * weekly hours ("every Monday 09:00-13:00"). A leave is the exception that
 * suspends those hours for specific calendar dates, so it is its own table,
 * linked straight to Doctor - the same way DoctorAvailability is.
 */
@Entity
@Table(name = "doctor_leave")
public class DoctorLeave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "leave_id")
    private Integer leaveId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    /** Both inclusive: a leave from Monday to Wednesday blocks all three days. */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    public DoctorLeave() {
    }

    public DoctorLeave(Doctor doctor, LocalDate startDate, LocalDate endDate) {
        this.doctor = doctor;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public Integer getLeaveId() {
        return leaveId;
    }

    public void setLeaveId(Integer leaveId) {
        this.leaveId = leaveId;
    }

    public Doctor getDoctor() {
        return doctor;
    }

    public void setDoctor(Doctor doctor) {
        this.doctor = doctor;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }
}
