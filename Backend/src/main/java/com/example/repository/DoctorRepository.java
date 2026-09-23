package com.example.repository;

import com.example.entity.Doctor;
import com.example.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Integer> {

    /**
     * Reads the doctor with a row lock held until the transaction ends
     * (SELECT ... FOR UPDATE). Booking takes it before checking for clashes,
     * so two bookings for the same doctor run one after the other.
     */
    /** The doctor record behind a signed-in staff account. */
    Optional<Doctor> findByUser(User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Doctor d where d.doctorId = :id")
    Optional<Doctor> lockById(@Param("id") Integer id);
}
