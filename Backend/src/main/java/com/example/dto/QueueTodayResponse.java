package com.example.dto;

import java.util.List;

/** Everything the desk needs to draw its left column: the day, the clinic QR
 *  and one row per doctor. */
public class QueueTodayResponse {

    private String date;
    private String qrUrl;
    private List<DoctorQueueResponse> doctors;

    public QueueTodayResponse() {
    }

    public QueueTodayResponse(String date, String qrUrl, List<DoctorQueueResponse> doctors) {
        this.date = date;
        this.qrUrl = qrUrl;
        this.doctors = doctors;
    }

    public String getDate() {
        return date;
    }

    public String getQrUrl() {
        return qrUrl;
    }

    public List<DoctorQueueResponse> getDoctors() {
        return doctors;
    }
}
