package com.example.dto;

/**
 * A new time for an existing appointment. Doctor and treatment stay the same:
 * changing either is a new booking, not a reschedule.
 */
public class AppointmentRescheduleDTO {

    /** Local date and time, e.g. 2026-09-17T10:30 */
    private String datetime;

    public AppointmentRescheduleDTO() {
    }

    public String getDatetime() {
        return datetime;
    }

    public void setDatetime(String datetime) {
        this.datetime = datetime;
    }
}
