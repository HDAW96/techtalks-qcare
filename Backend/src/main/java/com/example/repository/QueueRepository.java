package com.example.repository;

import com.example.entity.Doctor;
import com.example.entity.Queue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface QueueRepository extends JpaRepository<Queue, Integer> {

  Optional<Queue> findByDoctorAndQueueDate(
      Doctor doctor,
      LocalDate queueDate);

  List<Queue> findByQueueDate(LocalDate queueDate);
}
