package com.example.repository;

import com.example.entity.Appointment;
import com.example.entity.Doctor;
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
     * What is already taken for one doctor between two moments. Used to work
     * out which start times are still free.
     */
    @Query("""
            select a from Appointment a
            join fetch a.medicalService
            where a.doctor = :doctor
              and a.status = 'BOOKED'
              and a.appointmentDatetime >= :from
              and a.appointmentDatetime < :to
            """)
    List<Appointment> findBookedForDoctorBetween(@Param("doctor") Doctor doctor,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
