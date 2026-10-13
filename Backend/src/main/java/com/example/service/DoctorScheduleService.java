package com.example.service;

import com.example.dto.DoctorLeaveDTO;
import com.example.dto.DoctorLeaveResponse;
import com.example.dto.DoctorScheduleDTO;
import com.example.entity.Doctor;
import com.example.entity.DoctorLeave;
import com.example.entity.Queue;
import com.example.entity.QueueTicket;
import com.example.entity.User;
import com.example.repository.AppointmentRepository;
import com.example.repository.DoctorLeaveRepository;
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
import java.time.format.DateTimeParseException;
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
    private final DoctorLeaveRepository leaveRepository;
    /** Only for the ticket label, so it matches every other screen. */
    private final QueueService queueService;

    public DoctorScheduleService(UserRepository userRepository,
            DoctorRepository doctorRepository,
            AppointmentRepository appointmentRepository,
            QueueRepository queueRepository,
            QueueTicketRepository ticketRepository,
            DoctorLeaveRepository leaveRepository,
            QueueService queueService) {
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.queueRepository = queueRepository;
        this.ticketRepository = ticketRepository;
        this.leaveRepository = leaveRepository;
        this.queueService = queueService;
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

        return DoctorScheduleDTO.of(doctor, today, appointments, inChair(doctor, today),
                queueService.ticketPrefixFor(doctor));
    }

    /**
     * The visit in the signed-in doctor's chair right now, or null when the
     * chair is empty. A checked-in booking and a walk-in are both visits, so
     * either may be returned; appointmentId says which it is.
     *
     * The same lookup as the schedule's nowServing, so the two cannot name
     * different visits.
     */
    @PreAuthorize("hasRole('DOCTOR')")
    @Transactional(readOnly = true)
    public DoctorScheduleDTO.NowServing currentVisit(Integer userId) {
        Doctor doctor = doctorOf(userId);
        QueueTicket ticket = inChair(doctor, LocalDate.now());
        return ticket == null ? null
                : DoctorScheduleDTO.NowServing.of(ticket, queueService.ticketPrefixFor(doctor));
    }

    /**
     * Whoever the desk has called into this doctor's chair, or null. The
     * first one called in, if bad data ever put two there.
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
     * Blocks a range of calendar days for the signed-in doctor - vacation or
     * sick leave. BookingService reads this same table, through the same
     * DoctorLeaveRepository.existsForDoctorOnDate query, so a day blocked
     * here disappears from the booking screen and refuses a booking or
     * reschedule the moment it is saved.
     *
     * Deliberately does not touch appointments already BOOKED inside the new
     * range - resolving that clash (cancel them? refuse the leave?) is a
     * desk decision, not one this endpoint should make silently.
     */
    @PreAuthorize("hasRole('DOCTOR')")
    @Transactional
    public DoctorLeaveResponse addLeave(Integer userId, DoctorLeaveDTO request) {
        Doctor doctor = doctorOf(userId);
        LocalDate start = parseDate(request.getStartDate(), "startDate");
        LocalDate end = parseDate(request.getEndDate(), "endDate");

        if (end.isBefore(start)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LEAVE_RANGE_INVALID",
                    "The end date must not be before the start date.");
        }
        if (end.isBefore(LocalDate.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LEAVE_RANGE_INVALID",
                    "That leave has already passed. Please choose a current or future date.");
        }

        DoctorLeave leave = new DoctorLeave(doctor, start, end);
        leaveRepository.save(leave);
        return DoctorLeaveResponse.of(leave);
    }

    /** The signed-in doctor's own leave, earliest first, so they can review it. */
    @PreAuthorize("hasRole('DOCTOR')")
    @Transactional(readOnly = true)
    public List<DoctorLeaveResponse> myLeave(Integer userId) {
        Doctor doctor = doctorOf(userId);
        return leaveRepository.findByDoctorOrderByStartDateAsc(doctor).stream()
                .map(DoctorLeaveResponse::of)
                .toList();
    }

    private LocalDate parseDate(String date, String field) {
        if (date == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LEAVE_RANGE_INVALID",
                    "Please provide a " + field + ".");
        }
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LEAVE_RANGE_INVALID",
                    "That " + field + " is not a valid date.");
        }
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
