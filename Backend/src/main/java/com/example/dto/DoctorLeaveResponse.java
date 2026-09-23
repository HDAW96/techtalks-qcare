package com.example.dto;

import com.example.entity.DoctorLeave;

public class DoctorLeaveResponse {

    private Integer leaveId;
    private String startDate;
    private String endDate;

    public DoctorLeaveResponse() {
    }

    public static DoctorLeaveResponse of(DoctorLeave leave) {
        DoctorLeaveResponse dto = new DoctorLeaveResponse();
        dto.leaveId = leave.getLeaveId();
        dto.startDate = leave.getStartDate().toString();
        dto.endDate = leave.getEndDate().toString();
        return dto;
    }

    public Integer getLeaveId() {
        return leaveId;
    }

    public String getStartDate() {
        return startDate;
    }

    public String getEndDate() {
        return endDate;
    }
}
