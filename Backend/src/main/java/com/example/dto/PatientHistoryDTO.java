package com.example.dto;

import com.example.entity.Appointment;
import com.example.entity.Patient;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Who the patient is and what was done at each of their finished visits,
 * most recent first - what the doctor's history panel shows.
 */
public class PatientHistoryDTO {

    private Integer patientId;
    private String patientName;
    private List<Visit> visits;

    public PatientHistoryDTO() {
    }

    public static PatientHistoryDTO of(Patient patient, List<Appointment> completed) {
        PatientHistoryDTO dto = new PatientHistoryDTO();
        dto.patientId = patient.getPatientId();
        dto.patientName = patient.getFullName();
        dto.visits = completed.stream().map(Visit::of).toList();
        return dto;
    }

    public Integer getPatientId() {
        return patientId;
    }

    public String getPatientName() {
        return patientName;
    }

    public List<Visit> getVisits() {
        return visits;
    }

    /** One finished treatment. */
    public static class Visit {

        private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

        private Integer appointmentId;
        private String datetime;
        private String service;
        private String doctor;
        private String notes;

        public Visit() {
        }

        static Visit of(Appointment appointment) {
            Visit visit = new Visit();
            visit.appointmentId = appointment.getAppointmentId();
            visit.datetime = appointment.getAppointmentDatetime().format(LOCAL);
            visit.service = appointment.getMedicalService().getServiceName();
            visit.doctor = appointment.getDoctor().getUser().getFullName();
            // The doctor's notes from finishing the visit (SCRUM-74). Null
            // when none were written. Doctors only: AppointmentResponse, which
            // the patient's own screens read, deliberately leaves them out.
            visit.notes = appointment.getClinicalNotes();
            return visit;
        }

        public Integer getAppointmentId() {
            return appointmentId;
        }

        public String getDatetime() {
            return datetime;
        }

        public String getService() {
            return service;
        }

        public String getDoctor() {
            return doctor;
        }

        public String getNotes() {
            return notes;
        }
    }
}
