package com.example.service;

import com.example.dto.DoctorScheduleDTO;
import com.example.entity.Doctor;
import com.example.entity.Queue;
import com.example.entity.QueueTicket;
import com.example.entity.User;
import com.example.repository.AppointmentRepository;
import com.example.repository.DoctorRepository;
import com.example.repository.QueueRepository;
import com.example.repository.QueueTicketRepository;
import com.example.repository.UserRepository;
import com.example.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * The doctor's own day: today's appointments, and who is in their chair.
 *
 * Two separate things are joined here because they are one screen. The
 * appointments come from the booking side; the person in the chair comes from
 * the walk-in queue the desk is running, and may have no appointment at all.
 */
@Service
public class DoctorScheduleService {

    private static final String IN_CHAIR = QueueService.IN_CHAIR;

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final QueueRepository queueRepository;
    private final QueueTicketRepository ticketRepository;

    public DoctorScheduleService(UserRepository userRepository,
            DoctorRepository doctorRepository,
            AppointmentRepository appointmentRepository,
            QueueRepository queueRepository,
            QueueTicketRepository ticketRepository) {
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.queueRepository = queueRepository;
        this.ticketRepository = ticketRepository;
    }

    /**
     * The signed-in doctor's schedule for today.
     *
     * Guarded twice, on purpose. @PreAuthorize refuses anyone whose token does
     * not say DOCTOR, before a query runs. doctorOf then resolves the doctor
     * record from the database, so an account that has stopped being a doctor
     * since its token was issued gets nothing either - the same reason
     * PatientHistoryService reads the role from the database rather than
     * trusting the token.
     */
    @PreAuthorize("hasRole('DOCTOR')")
    @Transactional(readOnly = true)
    public DoctorScheduleDTO todaysSchedule(Integer userId) {
        Doctor doctor = doctorOf(userId);
        LocalDate today = LocalDate.now();

        var appointments = appointmentRepository.findForDoctorBetween(
                doctor, today.atStartOfDay(), today.plusDays(1).atStartOfDay());

        return DoctorScheduleDTO.of(doctor, today, appointments, inChair(doctor, today));
    }

    /**
     * Whoever the desk has called into this doctor's chair, or null.
     *
     * No queue open today simply means an empty chair, not an error: a doctor
     * seeing only booked patients never has one.
     */
    private QueueTicket inChair(Doctor doctor, LocalDate date) {
        Queue queue = queueRepository.findByDoctorAndQueueDate(doctor, date).orElse(null);
        if (queue == null) {
            return null;
        }
        List<QueueTicket> called = ticketRepository.findByQueueAndStatusFetched(queue, IN_CHAIR);
        return called.isEmpty() ? null : called.get(0);
    }

    /**
     * The doctor record behind the signed-in account. A staff account with the
     * DOCTOR role but no doctor row is a seeding mistake, not something a
     * caller can cause, so it answers plainly rather than pretending.
     */
    private Doctor doctorOf(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "Please sign in again."));
        return doctorRepository.findByUser(user)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "NOT_A_DOCTOR",
                        "Only doctors have a schedule."));
    }
}
