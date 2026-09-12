package com.example.dto;

/**
 * What the booking screen sends: which doctor, which treatment, and when.
 *
 * The patient is deliberately not in here - it comes from the caller's token,
 * so nobody can book an appointment in someone else's name.
 */
public class AppointmentBookingDTO {

    private Integer doctorId;
    private Integer serviceId;
    /** Local date and time, e.g. 2026-09-17T10:30 */
    private String datetime;

    public AppointmentBookingDTO() {
    }

    public Integer getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(Integer doctorId) {
        this.doctorId = doctorId;
    }

    public Integer getServiceId() {
        return serviceId;
    }

    public void setServiceId(Integer serviceId) {
        this.serviceId = serviceId;
    }

    public String getDatetime() {
        return datetime;
    }

    public void setDatetime(String datetime) {
        this.datetime = datetime;
    }
}
