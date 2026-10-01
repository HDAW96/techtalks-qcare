package com.example.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

/**
 * A treatment the clinic offers. The duration drives both the bookable time
 * slots and the queue's estimated waiting time.
 */
@Entity
@Table(name = "service")
public class MedicalService {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "service_id", nullable = false)
    private Integer serviceId;

    @Column(name = "name", nullable = false)
    private String serviceName;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    /**
     * Deactivated services stay in old appointments and history but can't be
     * booked.
     *
     * The column default matters: ddl-auto=update adds this column to tables
     * that already hold services, and without a default MySQL would fill the
     * existing rows with 0 - every service inactive, booking broken.
     */
    @Column(name = "active", nullable = false)
    @ColumnDefault("true")
    private Boolean active = true;

    public MedicalService() {
    }

    public MedicalService(String serviceName, Integer durationMinutes) {
        this.serviceName = serviceName;
        this.durationMinutes = durationMinutes;
    }

    public Integer getServiceId() {
        return serviceId;
    }

    public void setServiceId(Integer serviceId) {
        this.serviceId = serviceId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
