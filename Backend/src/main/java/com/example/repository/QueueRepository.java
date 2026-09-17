package com.example.repository;

import com.example.entity.Doctor;
import com.example.entity.Queue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface QueueRepository extends JpaRepository<Queue, Integer> {

    /**
     * One doctor has at most one queue per day, so opening a queue that is
     * already open must find this one rather than insert a second.
     */
    Optional<Queue> findByDoctorAndQueueDate(Doctor doctor, LocalDate queueDate);

    /**
     * Every queue open on a date.
     *
     * The receptionist is fetched with it because the desk lists "opened by"
     * against each doctor, and the association is lazy: without the fetch,
     * reading that name costs two more queries per row.
     */
    @Query("""
            select q from Queue q
            left join fetch q.receptionist r
            left join fetch r.user
            where q.queueDate = :queueDate
            """)
    List<Queue> findByQueueDate(@Param("queueDate") LocalDate queueDate);
}
