package com.example.repository;

import com.example.entity.Patient;
import com.example.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Integer> {

    /**
     * A returning walk-in must be found rather than inserted again - the
     * phone alone is not enough, because family members share one.
     */
    Optional<Patient> findByPhoneNumberAndFullName(String phoneNumber, String fullName);

    Optional<Patient> findByUser(User user);
}
