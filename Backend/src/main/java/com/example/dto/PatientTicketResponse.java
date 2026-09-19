package com.example.dto;

/**
 * A patient's own ticket, as their phone shows it. Never anyone else's name,
 * phone, id or code: only this ticket and who is in the chair.
 *
 * `accessCode` is filled in only in the answer to joining. It is how someone
 * without an account finds their ticket again, so it is handed over once and
 * kept by their browser.
 */
public class PatientTicketResponse {

    private Integer ticketId;
    /** e.g. "R-14" */
    private String ticket;
    private String status;
    /** People ahead, the person in the chair included. 0 once in the chair. Null off the line. */
    private Integer position;
    /** Minutes until likely called. Null unless waiting. */
    private Integer etaMinutes;
    private String doctorName;
    private String service;
    /** The ticket in the chair right now, if anyone is. */
    private String nowServing;
    private String date;
    private String accessCode;

    public PatientTicketResponse() {
    }

    public PatientTicketResponse(Integer ticketId, String ticket, String status, Integer position,
            Integer etaMinutes, String doctorName, String service, String nowServing, String date,
            String accessCode) {
        this.ticketId = ticketId;
        this.ticket = ticket;
        this.status = status;
        this.position = position;
        this.etaMinutes = etaMinutes;
        this.doctorName = doctorName;
        this.service = service;
        this.nowServing = nowServing;
        this.date = date;
        this.accessCode = accessCode;
    }

    public Integer getTicketId() {
        return ticketId;
    }

    public String getTicket() {
        return ticket;
    }

    public String getStatus() {
        return status;
    }

    public Integer getPosition() {
        return position;
    }

    public Integer getEtaMinutes() {
        return etaMinutes;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public String getService() {
        return service;
    }

    public String getNowServing() {
        return nowServing;
    }

    public String getDate() {
        return date;
    }

    public String getAccessCode() {
        return accessCode;
    }
}
