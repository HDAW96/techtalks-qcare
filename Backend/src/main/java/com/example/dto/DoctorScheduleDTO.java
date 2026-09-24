package com.example.dto;

import com.example.entity.Appointment;
import com.example.entity.Doctor;
import com.example.entity.QueueTicket;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * A doctor's own day: the appointments booked with them today, and whoever is
 * in their chair right now.
 *
 * Doctor-only, and it carries patients' names, so it must never be served to
 * anyone else - see the rule in SecurityConfig.
 *
 * Every row carries patientId and patientName, including nowServing, because
 * the treatment-history panel (SCRUM-72) is opened from a row and needs both.
 */
public class DoctorScheduleDTO {

    private Integer doctorId;
    private String doctorName;
    /** The day this schedule is for, as yyyy-MM-dd. */
    private String date;
    private List<Visit> appointments;
    /** Null when the chair is empty. */
    private NowServing nowServing;

    public DoctorScheduleDTO() {
    }

    /**
     * @param ticketPrefix this doctor's label prefix from QueueService, so the
     *                     doctor sees the same ticket label as the patient
     */
    public static DoctorScheduleDTO of(Doctor doctor, LocalDate date,
            List<Appointment> appointments, QueueTicket inChair, String ticketPrefix) {
        DoctorScheduleDTO dto = new DoctorScheduleDTO();
        dto.doctorId = doctor.getDoctorId();
        dto.doctorName = doctor.getUser().getFullName();
        dto.date = date.toString();
        dto.appointments = appointments.stream().map(Visit::of).toList();
        dto.nowServing = inChair == null ? null : NowServing.of(inChair, ticketPrefix);
        return dto;
    }

    public Integer getDoctorId() {
        return doctorId;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public String getDate() {
        return date;
    }

    public List<Visit> getAppointments() {
        return appointments;
    }

    public NowServing getNowServing() {
        return nowServing;
    }

    /** One booked visit on the doctor's day. */
    public static class Visit {

        private static final DateTimeFormatter LOCAL =
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

        private Integer appointmentId;
        private String datetime;
        private Integer patientId;
        private String patientName;
        private String service;
        private Integer minutes;
        /** BOOKED, COMPLETED, CANCELLED or MISSED. */
        private String status;

        public Visit() {
        }

        static Visit of(Appointment appointment) {
            Visit visit = new Visit();
            visit.appointmentId = appointment.getAppointmentId();
            visit.datetime = appointment.getAppointmentDatetime().format(LOCAL);
            visit.patientId = appointment.getPatient().getPatientId();
            visit.patientName = appointment.getPatient().getFullName();
            visit.service = appointment.getMedicalService().getServiceName();
            visit.minutes = appointment.getMedicalService().getDurationMinutes();
            visit.status = appointment.getStatus();
            return visit;
        }

        public Integer getAppointmentId() {
            return appointmentId;
        }

        public String getDatetime() {
            return datetime;
        }

        public Integer getPatientId() {
            return patientId;
        }

        public String getPatientName() {
            return patientName;
        }

        public String getService() {
            return service;
        }

        public Integer getMinutes() {
            return minutes;
        }

        public String getStatus() {
            return status;
        }
    }

    /** Whoever the desk has called into this doctor's chair. */
    public static class NowServing {

        private static final DateTimeFormatter LOCAL =
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

        private Integer ticketId;
        /** Already formatted for display, e.g. "K-14" for Dr Karim. */
        private String ticket;
        private Integer patientId;
        private String patientName;
        private String service;
        private Integer minutes;
        /** When the desk called them in. Null only if the data is odd. */
        private String startedAt;

        public NowServing() {
        }

        static NowServing of(QueueTicket ticket, String prefix) {
            NowServing now = new NowServing();
            now.ticketId = ticket.getTicketId();
            now.ticket = prefix + "-" + ticket.getTicketNumber();
            now.patientId = ticket.getPatient().getPatientId();
            now.patientName = ticket.getPatient().getFullName();
            now.service = ticket.getMedicalService().getServiceName();
            now.minutes = ticket.getMedicalService().getDurationMinutes();
            now.startedAt = ticket.getStartedAt() == null
                    ? null
                    : ticket.getStartedAt().format(LOCAL);
            return now;
        }

        public Integer getTicketId() {
            return ticketId;
        }

        public String getTicket() {
            return ticket;
        }

        public Integer getPatientId() {
            return patientId;
        }

        public String getPatientName() {
            return patientName;
        }

        public String getService() {
            return service;
        }

        public Integer getMinutes() {
            return minutes;
        }

        public String getStartedAt() {
            return startedAt;
        }
    }
}
