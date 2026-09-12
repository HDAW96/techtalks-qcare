package com.example.entity;

import jakarta.persistence.*;

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

    public Integer getServiceId() {
        return serviceId;

    }

    public String getServiceName() {
        return serviceName;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;

    }
}
