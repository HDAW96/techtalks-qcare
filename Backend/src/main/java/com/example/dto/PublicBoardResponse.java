package com.example.dto;

import java.util.List;

/**
 * One open queue as the waiting-room screen shows it. Public, so it carries
 * ticket numbers only: no names, phones, patient ids or access codes.
 */
public class PublicBoardResponse {

    private Integer doctorId;
    private String doctorName;
    private String status;
    private String nowServing;
    private List<Entry> entries;

    public PublicBoardResponse() {
    }

    public PublicBoardResponse(Integer doctorId, String doctorName, String status,
            String nowServing, List<Entry> entries) {
        this.doctorId = doctorId;
        this.doctorName = doctorName;
        this.status = status;
        this.nowServing = nowServing;
        this.entries = entries;
    }

    public Integer getDoctorId() {
        return doctorId;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public String getStatus() {
        return status;
    }

    public String getNowServing() {
        return nowServing;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    /** One place in the line: the ticket number, where it stands, and the wait. */
    public static class Entry {

        private String ticket;
        private String status;
        private Integer etaMinutes;

        public Entry() {
        }

        public Entry(String ticket, String status, Integer etaMinutes) {
            this.ticket = ticket;
            this.status = status;
            this.etaMinutes = etaMinutes;
        }

        public String getTicket() {
            return ticket;
        }

        public String getStatus() {
            return status;
        }

        public Integer getEtaMinutes() {
            return etaMinutes;
        }
    }
}
