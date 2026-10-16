package com.example.repository;

import com.example.entity.Appointment;
import com.example.entity.MedicalService;
import com.example.entity.Patient;
import com.example.entity.Queue;
import com.example.entity.QueueTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface QueueTicketRepository extends JpaRepository<QueueTicket, Integer> {

    /**
     * Every ticket in one queue, oldest first.
     *
     * The patient, treatment and any appointment are fetched with it because
     * the desk shows all three on every row, and the order is fixed here so
     * two screens reading the same queue cannot disagree about it.
     */
    @Query("""
            select t from QueueTicket t
            join fetch t.patient
            join fetch t.medicalService
            left join fetch t.appointment
            where t.queue = :queue
            order by t.joinedAt, t.ticketId
            """)
    List<QueueTicket> findByQueue(@Param("queue") Queue queue);

    /** How many are still waiting - the number the desk shows per doctor. */
    long countByQueueAndStatus(Queue queue, String status);

    /** The last number given out in this queue, so the next one follows it. */
    @Query("select max(t.ticketNumber) from QueueTicket t where t.queue = :queue")
    Integer highestTicketNumber(@Param("queue") Queue queue);

    /**
     * Someone sent to the back of the line is still in the queue, so a second
     * ticket for them would be a duplicate, not a new visit.
     */
    boolean existsByQueueAndPatientAndStatusIn(Queue queue, Patient patient, List<String> statuses);

    /**
     * The tickets in one queue with a given status, patient, treatment and
     * any appointment fetched. Used for the one IN_CHAIR ticket on a doctor's
     * schedule, which needs to say which booking (if any) it is.
     *
     * A List, not an Optional, for the same reason as the method below: the
     * one-chair rule is enforced in QueueService, and if bad data ever broke
     * it this should show the wrong person rather than crash the doctor's
     * whole screen with "more than one result".
     */
    @Query("""
            select t from QueueTicket t
            join fetch t.patient
            join fetch t.medicalService
            left join fetch t.appointment
            where t.queue = :queue
              and t.status = :status
            order by t.startedAt, t.ticketId
            """)
    List<QueueTicket> findByQueueAndStatusFetched(@Param("queue") Queue queue,
            @Param("status") String status);

    /** How a patient without an account finds their own ticket again. */
    Optional<QueueTicket> findByAccessCode(String accessCode);

    @Query("select t.queue.queueId from QueueTicket t where t.ticketId = :ticketId")
    Optional<Integer> findQueueIdByTicketId(@Param("ticketId") Integer ticketId);

    @Query("select t.queue.queueId from QueueTicket t where t.accessCode = :accessCode")
    Optional<Integer> findQueueIdByAccessCode(@Param("accessCode") String accessCode);

    Optional<QueueTicket> findByAppointment(Appointment appointment);

    /**
     * Every active ticket a patient holds on one date, across all doctors.
     * A List, not an Optional: a patient joining a second doctor's queue on
     * the same day must be caught as ALREADY_IN_QUEUE, not crash with "more
     * than one result".
     *
     * Not called anywhere yet, so one-ticket-per-day across doctors is not
     * enforced: addWalkIn only checks the one queue. The patient join
     * endpoint (SCRUM-56, next step) is where this should be used.
     */
    @Query("""
            SELECT t
            FROM QueueTicket t
            WHERE t.patient = :patient
              AND t.queue.queueDate = :queueDate
              AND t.status IN ('WAITING', 'IN_CHAIR')
            """)
    List<QueueTicket> findActiveTicketForPatientOnDate(
            @Param("patient") Patient patient,
            @Param("queueDate") LocalDate queueDate);

    /** Whether any queue ticket, whatever its status, uses this service. */
    boolean existsByMedicalService(MedicalService service);

    /** Tickets whose queue's day falls in the range, both ends included. */
    List<QueueTicket> findByQueueQueueDateBetween(LocalDate from, LocalDate to);
}
