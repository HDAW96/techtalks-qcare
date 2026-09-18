package com.example.dto;

import com.example.entity.Appointment;

import java.time.format.DateTimeFormatter;

/**
 * An appointment in the shape the appointment lists already read: the doctor's
 * name and the treatment name are included so a screen does not have to look
 * them up separately.
 */
public class AppointmentResponse {

    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private Integer id;
    private Integer doctorId;
    private String doctor;
    private Integer serviceId;
    private String service;
    private Integer minutes;
    private String datetime;
    private String status;

    public AppointmentResponse() {
    }

    public static AppointmentResponse of(Appointment appointment) {
        AppointmentResponse dto = new AppointmentResponse();
        dto.fill(appointment);
        return dto;
    }

    /** Shared with DeskAppointmentResponse, which adds who the patient is. */
    protected void fill(Appointment appointment) {
        id = appointment.getAppointmentId();
        doctorId = appointment.getDoctor().getDoctorId();
        doctor = appointment.getDoctor().getUser().getFullName();
        serviceId = appointment.getMedicalService().getServiceId();
        service = appointment.getMedicalService().getServiceName();
        minutes = appointment.getMedicalService().getDurationMinutes();
        datetime = appointment.getAppointmentDatetime().format(LOCAL);
        status = appointment.getStatus();
    }

    public Integer getId() {
        return id;
    }

    public Integer getDoctorId() {
        return doctorId;
    }

    public String getDoctor() {
        return doctor;
    }

    public Integer getServiceId() {
        return serviceId;
    }

    public String getService() {
        return service;
    }

    public Integer getMinutes() {
        return minutes;
    }

    public String getDatetime() {
        return datetime;
    }

    public String getStatus() {
        return status;
    }
}
