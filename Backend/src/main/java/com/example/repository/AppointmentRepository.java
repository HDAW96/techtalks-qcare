package com.example.repository;

import com.example.entity.Appointment;
import com.example.entity.Doctor;
import com.example.entity.MedicalService;
import com.example.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Integer> {

    /**
     * The doctor and treatment are fetched with the appointment because every
     * caller needs the doctor's name and the treatment's length - without
     * this each row would trigger two more queries.
     */
    @Query("""
            select a from Appointment a
            join fetch a.doctor d
            join fetch d.user
            join fetch a.medicalService
            where a.patient = :patient
            order by a.appointmentDatetime
            """)
    List<Appointment> findForPatient(@Param("patient") Patient patient);

    /**
     * A patient's finished treatments, most recent first, for the doctor's
     * history view. Booked, cancelled and missed visits are left out: nothing
     * was done at them.
     */
    @Query("""
            select a from Appointment a
            join fetch a.doctor d
            join fetch d.user
            join fetch a.medicalService
            where a.patient = :patient
              and a.status = 'COMPLETED'
            order by a.appointmentDatetime desc
            """)
    List<Appointment> findCompletedForPatient(@Param("patient") Patient patient);

    /**
     * What is already taken for one doctor between two moments. Used to work
     * out which start times are still free.
     */
    @Query("""
            select a from Appointment a
            join fetch a.medicalService
            where a.doctor = :doctor
              and a.status in ('BOOKED', 'CHECKED_IN')
              and a.appointmentDatetime >= :from
              and a.appointmentDatetime < :to
            """)
    List<Appointment> findBookedForDoctorBetween(@Param("doctor") Doctor doctor,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Every appointment in the clinic between two moments, whatever its
     * status, for the reception desk. The patient is fetched too, because the
     * desk needs to know who each appointment is for.
     */
    @Query("""
            select a from Appointment a
            join fetch a.patient
            join fetch a.doctor d
            join fetch d.user
            join fetch a.medicalService
            where a.appointmentDatetime >= :from
              and a.appointmentDatetime < :to
            order by a.appointmentDatetime, d.doctorId
            """)
    List<Appointment> findAllBetween(@Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * One doctor's whole day, earliest first, whatever the status.
     *
     * Cancelled and completed visits are included on purpose: the doctor's
     * own schedule should show that a slot freed up or is already finished,
     * not silently drop it. The caller decides what to grey out.
     *
     * The patient and treatment are fetched with it because every row shows
     * both, and the schedule carries the patient id so the history panel can
     * be opened from a row.
     */
    @Query("""
            select a from Appointment a
            join fetch a.patient
            join fetch a.medicalService
            where a.doctor = :doctor
              and a.appointmentDatetime >= :from
              and a.appointmentDatetime < :to
            order by a.appointmentDatetime
            """)
    List<Appointment> findForDoctorBetween(@Param("doctor") Doctor doctor,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** Whether any appointment, whatever its status, uses this service. */
    boolean existsByMedicalService(MedicalService service);

    /**
     * Appointments from `start` up to, but not including, `endExclusive`.
     * For a whole-day range pass the day after the last day at 00:00, or the
     * last day's appointments are silently left out.
     */
    List<Appointment> findByAppointmentDatetimeGreaterThanEqualAndAppointmentDatetimeLessThan(
            LocalDateTime start, LocalDateTime endExclusive);
}
