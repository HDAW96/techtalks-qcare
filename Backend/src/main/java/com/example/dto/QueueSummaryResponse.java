package com.example.dto;

import com.example.entity.Queue;

/** One open queue as the desk lists it, next to its doctor. */
public class QueueSummaryResponse {

    private Integer queueId;
    private String status;
    /** The receptionist who opened it, for the desk's "opened by" line. */
    private String openedBy;
    private long waitingCount;

    public QueueSummaryResponse() {
    }

    public static QueueSummaryResponse of(Queue queue, long waitingCount) {
        QueueSummaryResponse dto = new QueueSummaryResponse();
        dto.queueId = queue.getQueueId();
        dto.status = queue.getStatus();
        dto.openedBy = queue.getReceptionist() == null
                ? null
                : queue.getReceptionist().getUser().getFullName();
        dto.waitingCount = waitingCount;
        return dto;
    }

    public Integer getQueueId() {
        return queueId;
    }

    public String getStatus() {
        return status;
    }

    public String getOpenedBy() {
        return openedBy;
    }

    public long getWaitingCount() {
        return waitingCount;
    }
}
