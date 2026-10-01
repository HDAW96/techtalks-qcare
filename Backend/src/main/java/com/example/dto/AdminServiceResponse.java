package com.example.dto;

import com.example.entity.MedicalService;

/**
 * A treatment as the admin's services page needs it. Same field names as the
 * public ServiceResponse (`minutes`, not durationMinutes), so the frontend
 * reads a service the same way everywhere.
 *
 * `inUse` says whether any appointment or queue ticket refers to it, so the
 * page can offer only "Deactivate" for a service with history.
 */
public class AdminServiceResponse {

    private Integer id;
    private String name;
    private Integer minutes;
    private Boolean active;
    private Boolean inUse;

    public AdminServiceResponse() {
    }

    public static AdminServiceResponse of(MedicalService service, boolean inUse) {
        AdminServiceResponse dto = new AdminServiceResponse();
        dto.id = service.getServiceId();
        dto.name = service.getServiceName();
        dto.minutes = service.getDurationMinutes();
        dto.active = service.getActive();
        dto.inUse = inUse;
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

    public Boolean getActive() {
        return active;
    }

    public Boolean getInUse() {
        return inUse;
    }
}
