package com.example.dto;

import java.util.List;

/** One doctor's board: the rows, and the moment they were read. */
public class QueueTicketsResponse {

    private Integer queueId;
    /** "12:41" - the desk shows it so a stale screen is visible as stale. */
    private String updatedAt;
    private List<QueueTicketResponse> entries;

    public QueueTicketsResponse() {
    }

    public QueueTicketsResponse(Integer queueId, String updatedAt, List<QueueTicketResponse> entries) {
        this.queueId = queueId;
        this.updatedAt = updatedAt;
        this.entries = entries;
    }

    public Integer getQueueId() {
        return queueId;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public List<QueueTicketResponse> getEntries() {
        return entries;
    }
}
