package com.example.dto;

import com.example.entity.QueueTicket;

/**
 * What the doctor's "complete the current visit" call finished: the visit's
 * queue ticket and, when it came from a booking, that booking. A walk-in has
 * no appointmentId.
 */
public class CompletedVisitResponse {

    private Integer ticketId;
    private Integer appointmentId;
    /** Always COMPLETED: the visit is finished. */
    private String status;

    public CompletedVisitResponse() {
    }

    public static CompletedVisitResponse of(QueueTicket ticket) {
        CompletedVisitResponse dto = new CompletedVisitResponse();
        dto.ticketId = ticket.getTicketId();
        dto.appointmentId = ticket.getAppointment() == null
                ? null
                : ticket.getAppointment().getAppointmentId();
        dto.status = "COMPLETED";
        return dto;
    }

    public Integer getTicketId() {
        return ticketId;
    }

    public Integer getAppointmentId() {
        return appointmentId;
    }

    public String getStatus() {
        return status;
    }
}
