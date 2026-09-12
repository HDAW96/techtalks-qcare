package com.example.repository;

import com.example.entity.Doctor;
import com.example.entity.DoctorAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DoctorAvailabilityRepository extends JpaRepository<DoctorAvailability, Integer> {

    List<DoctorAvailability> findByDoctorAndDayOfWeek(Doctor doctor, Integer dayOfWeek);

    List<DoctorAvailability> findByDoctor(Doctor doctor);
}
