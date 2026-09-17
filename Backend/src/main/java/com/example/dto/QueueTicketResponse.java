package com.example.dto;

import com.example.entity.QueueTicket;

import java.time.format.DateTimeFormatter;

/**
 * One row of the desk board. This carries the patient's name and phone, so it
 * is for the desk only - the patient-facing board must never be given this
 * shape.
 */
public class QueueTicketResponse {

    private static final DateTimeFormatter LOCAL_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private Integer ticketId;
    /** Already formatted for display, e.g. "R-14". */
    private String ticket;
    private Integer patientId;
    private String patientName;
    private String phoneNumber;
    private String service;
    private String status;
    /** True when this person had an appointment rather than walking in. */
    private boolean booked;
    private String appointmentTime;
    private String joinedAt;
    /** 0 for the chair, then 1, 2, 3 for those waiting. Null once off the line. */
    private Integer position;
    /** Minutes until this person is likely called. Null unless they are waiting. */
    private Integer etaMinutes;

    public QueueTicketResponse() {
    }

    public static QueueTicketResponse of(QueueTicket ticket, Integer position, Integer etaMinutes) {
        QueueTicketResponse dto = new QueueTicketResponse();
        dto.ticketId = ticket.getTicketId();
        dto.ticket = "R-" + ticket.getTicketNumber();
        dto.patientId = ticket.getPatient().getPatientId();
        dto.patientName = ticket.getPatient().getFullName();
        dto.phoneNumber = ticket.getPatient().getPhoneNumber();
        dto.service = ticket.getMedicalService().getServiceName();
        dto.status = ticket.getStatus();
        dto.booked = ticket.getAppointment() != null;
        dto.appointmentTime = ticket.getAppointment() == null
                ? null
                : ticket.getAppointment().getAppointmentDatetime().format(LOCAL_DATETIME);
        dto.joinedAt = ticket.getJoinedAt().format(LOCAL_DATETIME);
        dto.position = position;
        dto.etaMinutes = etaMinutes;
        return dto;
    }

    public Integer getTicketId() {
        return ticketId;
    }

    public String getTicket() {
        return ticket;
    }

    public Integer getPatientId() {
        return patientId;
    }

    public String getPatientName() {
        return patientName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getService() {
        return service;
    }

    public String getStatus() {
        return status;
    }

    public boolean isBooked() {
        return booked;
    }

    public String getAppointmentTime() {
        return appointmentTime;
    }

    public String getJoinedAt() {
        return joinedAt;
    }

    public Integer getPosition() {
        return position;
    }

    public Integer getEtaMinutes() {
        return etaMinutes;
    }
}
