package com.example.dto;

/** POST /api/queues - open today's queue for one doctor. */
public class OpenQueueDTO {

    private Integer doctorId;

    public OpenQueueDTO() {
    }

    public Integer getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(Integer doctorId) {
        this.doctorId = doctorId;
    }
}
