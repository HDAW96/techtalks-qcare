package com.example.dto;

/** POST /api/doctors/me/leave. Dates as plain ISO strings, e.g. "2026-10-01". */
public class DoctorLeaveDTO {

    private String startDate;
    private String endDate;

    public DoctorLeaveDTO() {
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }
}
