package com.example.entity;

import jakarta.persistence.*;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * One person's place in a doctor's queue for a day. Walk-ins have no appointment,
 * so appointment_id is nullable.
 */
@Entity
@Table(
        name = "queue_ticket",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_queue_ticket_number",
                        columnNames = {"queue_id", "ticket_number"}
                )
        }
)
public class QueueTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ticket_id")
    private Integer ticketId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "queue_id", nullable = false)
    private Queue queue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private MedicalService medicalService;

    /** Null for a walk-in who arrived without booking. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = true)
    private Appointment appointment;

    @Column(name = "ticket_number", nullable = false)
    private Integer ticketNumber;

    /**
     * Secret code that lets a patient without an account find their ticket
     * again. It is filled in here rather than by the caller, because the
     * column is NOT NULL: a ticket built without one would fail on insert
     * with a raw database error instead of anything readable.
     */
    @Column(name = "access_code", nullable = false, unique = true, length = 32)
    private String accessCode = newAccessCode();

    /** WAITING, IN_CHAIR, DONE or SKIPPED - matches TICKET_STATUS in the frontend constants. */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "WAITING";

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt = LocalDateTime.now();

    /**
     * When this person was called into the chair. Null until then.
     * The waiting time shown to everyone behind them is only honest if it
     * counts down: without this, a treatment already half over would still be
     * charged to the queue at its full length.
     */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    public QueueTicket() {
    }

    public QueueTicket(
            Queue queue,
            Patient patient,
            MedicalService medicalService,
            Integer ticketNumber
    ) {
        this.queue = queue;
        this.patient = patient;
        this.medicalService = medicalService;
        this.ticketNumber = ticketNumber;
        this.status = "WAITING";
        this.joinedAt = LocalDateTime.now();
    }

    public Integer getTicketId() {
        return ticketId;
    }

    public void setTicketId(Integer ticketId) {
        this.ticketId = ticketId;
    }

    public Queue getQueue() {
        return queue;
    }

    public void setQueue(Queue queue) {
        this.queue = queue;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public MedicalService getMedicalService() {
        return medicalService;
    }

    public void setMedicalService(MedicalService medicalService) {
        this.medicalService = medicalService;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public void setAppointment(Appointment appointment) {
        this.appointment = appointment;
    }

    public Integer getTicketNumber() {
        return ticketNumber;
    }

    public void setTicketNumber(Integer ticketNumber) {
        this.ticketNumber = ticketNumber;
    }

    /**
     * 24 characters from a URL-safe alphabet, so the code can be put in a link
     * without escaping. SecureRandom, because this is the only thing standing
     * between a stranger and someone else's ticket.
     */
    private static String newAccessCode() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String getAccessCode() {
        return accessCode;
    }

    public void setAccessCode(String accessCode) {
        this.accessCode = accessCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }
}
