package com.example.repository;

import com.example.entity.Patient;
import com.example.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Integer> {

    /**
     * A returning walk-in must be found rather than inserted again - the
     * phone alone is not enough, because family members share one.
     */
    Optional<Patient> findByPhoneNumberAndFullName(String phoneNumber, String fullName);

    Optional<Patient> findByUser(User user);

    /**
     * The escaped search string comes from PatientSearchService. '!' is used
     * as the LIKE escape character because MySQL treats backslashes specially.
     */
    @Query("""
    SELECT p FROM Patient p
    WHERE :search IS NULL
       OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', :search, '%')) ESCAPE '!'
       OR p.phoneNumber LIKE CONCAT('%', :search, '%') ESCAPE '!'
    ORDER BY LOWER(p.fullName), p.patientId
    """)
    List<Patient> searchPatients(@Param("search") String search);
}
