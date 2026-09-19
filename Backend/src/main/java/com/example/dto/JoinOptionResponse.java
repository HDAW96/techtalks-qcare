package com.example.dto;

/**
 * One doctor a patient can join after scanning the clinic QR: only doctors
 * whose queue is open today are ever offered. No patient data at all.
 */
public class JoinOptionResponse {

    private Integer doctorId;
    private String doctorName;
    private String specialization;
    private long waitingCount;
    /** Roughly how long someone joining now would wait, in minutes. */
    private int etaMinutes;

    public JoinOptionResponse() {
    }

    public JoinOptionResponse(Integer doctorId, String doctorName, String specialization,
            long waitingCount, int etaMinutes) {
        this.doctorId = doctorId;
        this.doctorName = doctorName;
        this.specialization = specialization;
        this.waitingCount = waitingCount;
        this.etaMinutes = etaMinutes;
    }

    public Integer getDoctorId() {
        return doctorId;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public String getSpecialization() {
        return specialization;
    }

    public long getWaitingCount() {
        return waitingCount;
    }

    public int getEtaMinutes() {
        return etaMinutes;
    }
}
