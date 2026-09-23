package com.example.repository;

import com.example.entity.Doctor;
import com.example.entity.DoctorLeave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DoctorLeaveRepository extends JpaRepository<DoctorLeave, Integer> {

    /** A doctor's own leave, for their "my leave" screen. */
    List<DoctorLeave> findByDoctorOrderByStartDateAsc(Doctor doctor);

    /**
     * Whether one date falls inside any of this doctor's leave. This is the
     * single check both BookingService.availability (what the patient is
     * shown) and BookingService.requireBookable (what a booking or
     * reschedule is actually let through) call, so the two can never
     * disagree about whether a day is blocked.
     */
    @Query("""
            SELECT CASE WHEN COUNT(l) > 0 THEN true ELSE false END
            FROM DoctorLeave l
            WHERE l.doctor = :doctor
              AND :date BETWEEN l.startDate AND l.endDate
            """)
    boolean existsForDoctorOnDate(@Param("doctor") Doctor doctor, @Param("date") LocalDate date);
}
