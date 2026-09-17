package com.example.dto;

import com.example.entity.Doctor;

/**
 * A doctor on the desk's list for today. `queue` is null until someone opens
 * one, which is what the screen branches on to show "Open queue" instead of
 * "View queue".
 */
public class DoctorQueueResponse {

    private Integer doctorId;
    private String doctorName;
    /**
     * Whether the doctor has working hours on this weekday. A warning only:
     * the desk may still open a queue, because a doctor who came in on an
     * unscheduled day is a normal Saturday, not an error.
     */
    private boolean scheduledToday;
    private QueueSummaryResponse queue;

    public DoctorQueueResponse() {
    }

    public static DoctorQueueResponse of(Doctor doctor, boolean scheduledToday,
            QueueSummaryResponse queue) {
        DoctorQueueResponse dto = new DoctorQueueResponse();
        dto.doctorId = doctor.getDoctorId();
        dto.doctorName = doctor.getUser().getFullName();
        dto.scheduledToday = scheduledToday;
        dto.queue = queue;
        return dto;
    }

    public Integer getDoctorId() {
        return doctorId;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public boolean isScheduledToday() {
        return scheduledToday;
    }

    public QueueSummaryResponse getQueue() {
        return queue;
    }
}
