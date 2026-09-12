package com.example.repository;
import com.example.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface PatientRepository extends JpaRepository<Patient, Integer> {
    Optional<Patient> findByPhoneNumberAndFullName(String phoneNumber, String fullName);
}
