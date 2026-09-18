package com.example.service;

import com.example.dto.DoctorQueueResponse;
import com.example.dto.QueueSummaryResponse;
import com.example.dto.QueueTicketResponse;
import com.example.dto.QueueTicketsResponse;
import com.example.dto.QueueTodayResponse;
import com.example.dto.WalkInDTO;
import com.example.entity.Doctor;
import com.example.entity.MedicalService;
import com.example.entity.Patient;
import com.example.entity.Queue;
import com.example.entity.QueueTicket;
import com.example.entity.Receptionist;
import com.example.entity.User;
import com.example.repository.DoctorAvailabilityRepository;
import com.example.repository.DoctorRepository;
import com.example.repository.MedicalServiceRepository;
import com.example.repository.PatientRepository;
import com.example.repository.QueueRepository;
import com.example.repository.QueueTicketRepository;
import com.example.repository.ReceptionistRepository;
import com.example.repository.UserRepository;
import com.example.util.PhoneNumbers;
import com.example.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The walk-in queue: opening a doctor's day, putting people on it, and moving
 * them along it.
 *
 * Two rules shape everything here. One doctor has one chair, so at most one
 * ticket may be IN_CHAIR at a time. And the order people are called in is
 * decided by the server, never sent up by the desk - two receptionists on two
 * screens must not be able to disagree about who is next.
 */
@Service
public class QueueService {

    public static final String WAITING = "WAITING";
    public static final String IN_CHAIR = "IN_CHAIR";
    public static final String DONE = "DONE";
    public static final String SKIPPED = "SKIPPED";

    /** Still in the queue: a second ticket for the same person would be a duplicate. */
    private static final List<String> ACTIVE = List.of(WAITING, IN_CHAIR);

    /**
     * The only moves the desk may make. Anything else - reviving a finished
     * ticket, sending someone straight from the chair back to waiting - is
     * refused rather than quietly allowed, because a queue that can go
     * backwards cannot be trusted by the people reading it on their phones.
     */
    private static final Map<String, Set<String>> ALLOWED_MOVES = Map.of(
            WAITING, Set.of(IN_CHAIR, SKIPPED),
            IN_CHAIR, Set.of(DONE),
            SKIPPED, Set.of(WAITING),
            DONE, Set.of());

    /** Board order: the chair, then the line, then those set aside, then the finished. */
    private static final Map<String, Integer> DISPLAY_ORDER = Map.of(
            IN_CHAIR, 0, WAITING, 1, SKIPPED, 2, DONE, 3);

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final QueueRepository queueRepository;
    private final QueueTicketRepository ticketRepository;
    private final DoctorRepository doctorRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final MedicalServiceRepository serviceRepository;
    private final PatientRepository patientRepository;
    private final ReceptionistRepository receptionistRepository;
    private final UserRepository userRepository;

    public QueueService(QueueRepository queueRepository,
            QueueTicketRepository ticketRepository,
            DoctorRepository doctorRepository,
            DoctorAvailabilityRepository availabilityRepository,
            MedicalServiceRepository serviceRepository,
            PatientRepository patientRepository,
            ReceptionistRepository receptionistRepository,
            UserRepository userRepository) {
        this.queueRepository = queueRepository;
        this.ticketRepository = ticketRepository;
        this.doctorRepository = doctorRepository;
        this.availabilityRepository = availabilityRepository;
        this.serviceRepository = serviceRepository;
        this.patientRepository = patientRepository;
        this.receptionistRepository = receptionistRepository;
        this.userRepository = userRepository;
    }

    /** Every doctor, with today's queue where one has been opened. */
    @Transactional(readOnly = true)
    public QueueTodayResponse today() {
        LocalDate date = LocalDate.now();
        int weekday = date.getDayOfWeek().getValue();

        List<Queue> open = queueRepository.findByQueueDate(date);

        List<DoctorQueueResponse> doctors = doctorRepository.findAll().stream()
                .map(doctor -> {
                    Queue queue = open.stream()
                            .filter(q -> q.getDoctor().getDoctorId().equals(doctor.getDoctorId()))
                            .findFirst()
                            .orElse(null);
                    boolean scheduled = !availabilityRepository
                            .findByDoctorAndDayOfWeek(doctor, weekday).isEmpty();
                    QueueSummaryResponse summary = queue == null
                            ? null
                            : QueueSummaryResponse.of(queue,
                                    ticketRepository.countByQueueAndStatus(queue, WAITING));
                    return DoctorQueueResponse.of(doctor, scheduled, summary);
                })
                .toList();

        // One clinic, one QR per day: it points at the join page for this
        // date, and the patient chooses their doctor there.
        return new QueueTodayResponse(date.toString(), "/join?date=" + date, doctors);
    }

    /**
     * Opens today's queue for a doctor, or returns the one already open.
     *
     * Deliberately not an error the second time. Two receptionists pressing
     * "Open queue" at the same desk is a normal morning, and the second one
     * should land on the open queue rather than on a red message.
     */
    @Transactional
    public DoctorQueueResponse openQueue(Integer userId, Integer doctorId) {
        if (doctorId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DOCTOR_REQUIRED",
                    "Please choose a doctor.");
        }
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DOCTOR_NOT_FOUND",
                        "That doctor was not found."));
        LocalDate date = LocalDate.now();

        Queue queue = queueRepository.findByDoctorAndQueueDate(doctor, date)
                .orElseGet(() -> queueRepository.save(
                        new Queue(doctor, receptionistOf(userId), date)));

        boolean scheduled = !availabilityRepository
                .findByDoctorAndDayOfWeek(doctor, date.getDayOfWeek().getValue()).isEmpty();

        return DoctorQueueResponse.of(doctor, scheduled,
                QueueSummaryResponse.of(queue,
                        ticketRepository.countByQueueAndStatus(queue, WAITING)));
    }

    /** One doctor's board, in the order people are actually called. */
    @Transactional(readOnly = true)
    public QueueTicketsResponse tickets(Integer queueId) {
        Queue queue = queueOrThrow(queueId);
        return new QueueTicketsResponse(queueId, LocalDateTime.now().format(CLOCK), board(queue));
    }

    /**
     * Adds someone who walked in without an appointment.
     *
     * A returning walk-in must be found rather than inserted again: the clinic
     * knows them by phone plus name, because a family shares one number.
     */
    // READ_COMMITTED: see QueueRepository.lockById - the lock alone is not
    // enough on MySQL's default isolation (REPEATABLE READ), which would
    // still read the ticket-number and chair counts from a snapshot taken
    // before the wait. Same pattern as BookingService.book/reschedule.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public QueueTicketResponse addWalkIn(Integer queueId, WalkInDTO request) {
        // Locked first, and the transaction's only query against this queue's
        // row until it commits: two walk-ins posted at the same instant now
        // queue up here instead of both reading the same "highest ticket
        // number" and both trying to save it.
        queueRepository.lockById(queueId);
        Queue queue = queueOrThrow(queueId);
        if (!"OPEN".equals(queue.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "QUEUE_CLOSED",
                    "This queue is closed. Open it again to add anyone.");
        }

        String fullName = trimmed(request.getFullName());
        String phone = PhoneNumbers.normalise(request.getPhoneNumber());

        if (fullName == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NAME_REQUIRED",
                    "Please enter the patient's full name.");
        }
        if (!PhoneNumbers.isValidLebanese(phone)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHONE_INVALID",
                    "Please enter a valid Lebanese phone number.");
        }
        if (request.getServiceId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SERVICE_REQUIRED",
                    "Please choose a treatment.");
        }
        MedicalService service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND",
                        "That treatment was not found."));

        Patient patient = patientRepository.findByPhoneNumberAndFullName(phone, fullName)
                .orElseGet(() -> patientRepository.save(new Patient(fullName, phone)));

        if (ticketRepository.existsByQueueAndPatientAndStatusIn(queue, patient, ACTIVE)) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_IN_QUEUE",
                    fullName + " is already in this queue.");
        }

        Integer highest = ticketRepository.highestTicketNumber(queue);
        QueueTicket ticket = new QueueTicket(queue, patient, service,
                highest == null ? 1 : highest + 1);
        ticketRepository.save(ticket);

        // Read the board back so the new row carries the same position and
        // waiting time everyone else on it was just given.
        String prefix = ticketPrefixes().getOrDefault(queue.getDoctor().getDoctorId(), "R");
        return board(queue).stream()
                .filter(row -> row.getTicketId().equals(ticket.getTicketId()))
                .findFirst()
                .orElseGet(() -> QueueTicketResponse.of(ticket, null, null, prefix));
    }

    /** Call in, skip, finish, or bring back - whichever the desk pressed. */
    // READ_COMMITTED for the same reason as addWalkIn: the lock alone does
    // not stop two "call in" requests both reading zero people in the chair.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public QueueTicketsResponse changeStatus(Integer queueId, Integer ticketId, String wanted) {
        queueRepository.lockById(queueId);
        Queue queue = queueOrThrow(queueId);
        QueueTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND",
                        "That ticket was not found."));

        // The ticket id alone would let one doctor's desk move another's queue
        // along.
        if (!ticket.getQueue().getQueueId().equals(queueId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND",
                    "That ticket is not in this queue.");
        }
        if (wanted == null || !ALLOWED_MOVES.containsKey(wanted)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "STATUS_INVALID",
                    "That is not a status a ticket can have.");
        }
        if (!ALLOWED_MOVES.get(ticket.getStatus()).contains(wanted)) {
            throw new ApiException(HttpStatus.CONFLICT, "MOVE_NOT_ALLOWED",
                    "This ticket cannot be moved there from where it is.");
        }
        if (IN_CHAIR.equals(wanted)
                && ticketRepository.countByQueueAndStatus(queue, IN_CHAIR) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "CHAIR_TAKEN",
                    "Someone is already in the chair. Finish with them first.");
        }

        ticket.setStatus(wanted);
        if (IN_CHAIR.equals(wanted)) {
            // What makes the waiting time shown to everyone behind count down.
            ticket.setStartedAt(LocalDateTime.now());
        }
        ticketRepository.save(ticket);

        // The whole board, not the one row: calling someone in changes every
        // waiting time behind them, so returning one row would leave the rest
        // of the screen wrong.
        return new QueueTicketsResponse(queueId, LocalDateTime.now().format(CLOCK), board(queue));
    }

    /**
     * The board in calling order, with each waiting person's position and
     * likely wait.
     *
     * The wait is the sum of the treatments in front of them, plus whatever is
     * left of the one in the chair - not its full length, which would tell
     * someone to come back in 45 minutes for a cleaning that is nearly over.
     */
    private List<QueueTicketResponse> board(Queue queue) {
        List<QueueTicket> tickets = new ArrayList<>(ticketRepository.findByQueue(queue));
        tickets.sort(Comparator
                .comparingInt((QueueTicket t) -> DISPLAY_ORDER.getOrDefault(t.getStatus(), 9))
                .thenComparing(QueueTicket::getJoinedAt)
                .thenComparing(QueueTicket::getTicketId));

        int minutesAhead = tickets.stream()
                .filter(t -> IN_CHAIR.equals(t.getStatus()))
                .findFirst()
                .map(QueueService::minutesRemaining)
                .orElse(0);

        String prefix = ticketPrefixes().getOrDefault(queue.getDoctor().getDoctorId(), "R");

        List<QueueTicketResponse> board = new ArrayList<>();
        int place = 0;
        for (QueueTicket ticket : tickets) {
            if (IN_CHAIR.equals(ticket.getStatus())) {
                // In the chair now: a waiting time would mean nothing.
                board.add(QueueTicketResponse.of(ticket, place++, null, prefix));
            } else if (WAITING.equals(ticket.getStatus())) {
                board.add(QueueTicketResponse.of(ticket, place++, minutesAhead, prefix));
                minutesAhead += ticket.getMedicalService().getDurationMinutes();
            } else {
                // Skipped or finished: off the line, so neither a place nor a wait.
                board.add(QueueTicketResponse.of(ticket, null, null, prefix));
            }
        }
        return board;
    }

    /**
     * Each doctor's label prefix, worked out across every doctor at once so a
     * clash between two first names is caught rather than shown as the same
     * letter on two boards. One letter by default (Rania, Karim -> R, K);
     * when two doctors' first names start the same way, both grow another
     * letter (Rania, Rami -> RA, RI). Compared case-insensitively.
     */
    private Map<Integer, String> ticketPrefixes() {
        List<Doctor> doctors = doctorRepository.findAll();
        Map<Integer, String> prefixes = new LinkedHashMap<>();
        for (Doctor doctor : doctors) {
            String firstName = firstNameOf(doctor);
            int length = 1;
            while (true) {
                String candidate = firstName.substring(0, Math.min(length, firstName.length()))
                        .toUpperCase();
                boolean clashes = doctors.stream()
                        .filter(other -> !other.getDoctorId().equals(doctor.getDoctorId()))
                        .anyMatch(other -> firstNameOf(other).toUpperCase().startsWith(candidate));
                if (!clashes || length >= firstName.length()) {
                    prefixes.put(doctor.getDoctorId(), candidate);
                    break;
                }
                length++;
            }
        }
        return prefixes;
    }

    /**
     * The name the label is built from, e.g. "Rania" out of "Dr. Rania
     * Haddad" - DataSeeder gives every doctor a "Dr." title, and a prefix
     * built from that would label every ticket "D-..." instead of "R-...".
     */
    private static String firstNameOf(Doctor doctor) {
        String[] words = doctor.getUser().getFullName().trim().split("\\s+");
        for (String word : words) {
            String bare = word.replace(".", "");
            if (!bare.equalsIgnoreCase("Dr") && !bare.isEmpty()) {
                return bare;
            }
        }
        return words[0];
    }

    /** How much of the treatment in the chair is left, never below zero. */
    private static int minutesRemaining(QueueTicket ticket) {
        int duration = ticket.getMedicalService().getDurationMinutes();
        if (ticket.getStartedAt() == null) {
            return duration;
        }
        long elapsed = Duration.between(ticket.getStartedAt(), LocalDateTime.now()).toMinutes();
        return (int) Math.max(0, duration - elapsed);
    }

    private Queue queueOrThrow(Integer queueId) {
        return queueRepository.findById(queueId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "QUEUE_NOT_FOUND",
                        "That queue was not found."));
    }

    /**
     * The desk record behind the signed-in account. A queue records who opened
     * it, and that has to be a real receptionist, not whoever holds a token.
     */
    private Receptionist receptionistOf(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "Please sign in again."));
        return receptionistRepository.findByUser(user)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "NOT_A_RECEPTIONIST",
                        "Only the front desk can open a queue."));
    }

    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim().replaceAll("[ ]+", " ");
        return cleaned.isEmpty() ? null : cleaned;
    }
}
