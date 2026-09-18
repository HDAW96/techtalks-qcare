package com.example.dto;

/** PATCH a ticket: the one field the desk buttons change. */
public class TicketStatusDTO {

    private String status;

    public TicketStatusDTO() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
