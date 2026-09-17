package com.example.repository;

import com.example.entity.Queue;
import com.example.entity.QueueTicket;
import com.example.entity.Patient;
import com.example.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface QueueTicketRepository extends JpaRepository<QueueTicket, Integer> {

  List<QueueTicket> findByQueue(Queue queue);

  Optional<QueueTicket> findByAccessCode(String accessCode);

  Optional<QueueTicket> findByAppointment(Appointment appointment);

  @Query("""
          SELECT t
          FROM QueueTicket t
          WHERE t.patient = :patient
            AND t.queue.queueDate = :queueDate
            AND t.status IN ('WAITING', 'IN_CHAIR')
      """)
  Optional<QueueTicket> findActiveTicketForPatientOnDate(
      @Param("patient") Patient patient,
      @Param("queueDate") LocalDate queueDate);
}
