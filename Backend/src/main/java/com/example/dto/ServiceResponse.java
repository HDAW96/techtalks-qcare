package com.example.dto;

import com.example.entity.MedicalService;

/**
 * A treatment as the booking screen needs it. The field is called `minutes`
 * rather than durationMinutes because that is what the frontend already reads.
 */
public class ServiceResponse {

    private Integer id;
    private String name;
    private Integer minutes;

    public ServiceResponse() {
    }

    public static ServiceResponse of(MedicalService service) {
        ServiceResponse dto = new ServiceResponse();
        dto.id = service.getServiceId();
        dto.name = service.getServiceName();
        dto.minutes = service.getDurationMinutes();
        return dto;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Integer getMinutes() {
        return minutes;
    }
}
