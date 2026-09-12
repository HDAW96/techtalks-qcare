package com.example.entity;

import jakarta.persistence.*;

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
}
