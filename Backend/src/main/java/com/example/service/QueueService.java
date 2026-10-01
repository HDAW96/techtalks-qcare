package com.example.service;

import com.example.dto.DoctorQueueResponse;
import com.example.dto.JoinOptionResponse;
import com.example.dto.JoinQueueDTO;
import com.example.dto.PatientTicketResponse;
import com.example.dto.PublicBoardResponse;
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
import java.util.Optional;
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

    private static final String OPEN = "OPEN";
    private static final String CLOSED = "CLOSED";

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
    private final QrSigner qrSigner;

    public QueueService(QueueRepository queueRepository,
            QueueTicketRepository ticketRepository,
            DoctorRepository doctorRepository,
            DoctorAvailabilityRepository availabilityRepository,
            MedicalServiceRepository serviceRepository,
            PatientRepository patientRepository,
            ReceptionistRepository receptionistRepository,
            UserRepository userRepository,
            QrSigner qrSigner) {
        this.queueRepository = queueRepository;
        this.ticketRepository = ticketRepository;
        this.doctorRepository = doctorRepository;
        this.availabilityRepository = availabilityRepository;
        this.serviceRepository = serviceRepository;
        this.patientRepository = patientRepository;
        this.receptionistRepository = receptionistRepository;
        this.userRepository = userRepository;
        this.qrSigner = qrSigner;
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
        // date, and the patient chooses their doctor there. Signed, so a
        // hand-typed or edited link is refused (QrSigner).
        return new QueueTodayResponse(date.toString(), qrSigner.joinUrl(date), doctors);
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
        // Closed earlier today and the doctor is back: opening it again is
        // the same button, not a second queue (one per doctor per day).
        if (CLOSED.equals(queue.getStatus())) {
            queue.setStatus(OPEN);
            queueRepository.save(queue);
        }

        boolean scheduled = !availabilityRepository
                .findByDoctorAndDayOfWeek(doctor, date.getDayOfWeek().getValue()).isEmpty();

        return DoctorQueueResponse.of(doctor, scheduled,
                QueueSummaryResponse.of(queue,
                        ticketRepository.countByQueueAndStatus(queue, WAITING)));
    }

    /**
     * Closes a doctor's queue for the day: nobody new can join it, by QR or at
     * the desk, and it leaves the patients' list and the public board. Those
     * already in it can still be called in and finished.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DoctorQueueResponse closeQueue(Integer queueId) {
        queueRepository.lockById(queueId);
        Queue queue = queueOrThrow(queueId);
        requireToday(queue);
        queue.setStatus(CLOSED);
        queueRepository.save(queue);

        Doctor doctor = queue.getDoctor();
        boolean scheduled = !availabilityRepository
                .findByDoctorAndDayOfWeek(doctor, queue.getQueueDate().getDayOfWeek().getValue())
                .isEmpty();
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
        requireToday(queue);
        if (!OPEN.equals(queue.getStatus())) {
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
        ServiceCatalogService.requireActive(service);

        Patient patient = patientRepository.findByPhoneNumberAndFullName(phone, fullName)
                .orElseGet(() -> patientRepository.save(new Patient(fullName, phone)));

        requireNoActiveTicketToday(patient, fullName + " is");
        QueueTicket ticket = issueTicket(queue, patient, service);

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
        requireToday(queue);
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

    /* ── Patients: joining by the clinic QR, and following their ticket ────── */

    /**
     * The doctors a patient can choose after scanning today's clinic QR: only
     * those whose queue is OPEN today. The QR is checked first, so an edited
     * or old code gets a clear answer before the patient fills anything in.
     */
    @Transactional(readOnly = true)
    public List<JoinOptionResponse> joinOptions(String date, String sig) {
        qrSigner.check(date, sig);
        return queueRepository.findByQueueDate(LocalDate.now()).stream()
                .filter(q -> OPEN.equals(q.getStatus()))
                .sorted(Comparator.comparing((Queue q) -> q.getDoctor().getDoctorId()))
                .map(q -> new JoinOptionResponse(
                        q.getDoctor().getDoctorId(),
                        q.getDoctor().getUser().getFullName(),
                        q.getDoctor().getSpecialization(),
                        ticketRepository.countByQueueAndStatus(q, WAITING),
                        waitForSomeoneJoiningNow(q)))
                .toList();
    }

    /**
     * Joins today's queue of the chosen doctor, from the QR's join page.
     *
     * Signed in (userId not null): the patient joins as themselves, and any
     * name or phone in the request is ignored. Not signed in: name and phone
     * are required, and the clinic's record for that pair is found or made,
     * exactly as at the desk. The answer carries the ticket's access code,
     * which is how someone without an account opens their ticket again.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PatientTicketResponse join(Integer userId, JoinQueueDTO request) {
        // Checked again here, never trusting that the page already did.
        qrSigner.check(request.getDate(), request.getSig());

        if (request.getDoctorId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DOCTOR_REQUIRED",
                    "Please choose a doctor.");
        }
        if (request.getServiceId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SERVICE_REQUIRED",
                    "Please choose a treatment.");
        }
        ApiException notOpen = new ApiException(HttpStatus.CONFLICT, "QUEUE_NOT_OPEN",
                "That doctor's queue is not open right now. Please choose another or ask at the desk.");
        Doctor doctor = doctorRepository.findById(request.getDoctorId()).orElseThrow(() -> notOpen);
        Queue found = queueRepository.findByDoctorAndQueueDate(doctor, LocalDate.now())
                .orElseThrow(() -> notOpen);

        // The same lock as the desk's walk-in, so a QR join and a desk walk-in
        // at the same moment cannot take the same ticket number.
        queueRepository.lockById(found.getQueueId());
        Queue queue = queueOrThrow(found.getQueueId());
        if (!OPEN.equals(queue.getStatus())) {
            throw notOpen;
        }
        MedicalService service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND",
                        "That treatment was not found."));
        ServiceCatalogService.requireActive(service);

        Patient patient = userId != null ? signedInPatient(userId) : walkInPatient(request);
        requireNoActiveTicketToday(patient, "You are");

        QueueTicket ticket = issueTicket(queue, patient, service);
        return patientView(ticket, true);
    }

    /** The signed-in patient's ticket today, if they are still in a queue. */
    @Transactional(readOnly = true)
    public Optional<PatientTicketResponse> myTicket(Integer userId) {
        Patient patient = signedInPatient(userId);
        return ticketRepository.findActiveTicketForPatientOnDate(patient, LocalDate.now()).stream()
                .findFirst()
                .map(ticket -> patientView(ticket, false));
    }

    /**
     * A ticket found by its access code, for patients without an account.
     * Only today's: yesterday's code left in a browser means nothing today.
     */
    @Transactional(readOnly = true)
    public PatientTicketResponse ticketByCode(String accessCode) {
        QueueTicket ticket = ticketRepository.findByAccessCode(accessCode)
                .filter(t -> t.getQueue().getQueueDate().equals(LocalDate.now()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND",
                        "That ticket was not found. It may be from another day."));
        return patientView(ticket, false);
    }

    /**
     * The waiting-room screen: every queue open today, with ticket numbers
     * only. Public, so it must never carry anything that identifies anyone.
     */
    @Transactional(readOnly = true)
    public List<PublicBoardResponse> publicBoard() {
        return queueRepository.findByQueueDate(LocalDate.now()).stream()
                .filter(q -> OPEN.equals(q.getStatus()))
                .sorted(Comparator.comparing((Queue q) -> q.getDoctor().getDoctorId()))
                .map(q -> {
                    List<QueueTicketResponse> rows = board(q);
                    List<PublicBoardResponse.Entry> entries = rows.stream()
                            .filter(r -> IN_CHAIR.equals(r.getStatus()) || WAITING.equals(r.getStatus()))
                            .map(r -> new PublicBoardResponse.Entry(
                                    r.getTicket(), r.getStatus(), r.getEtaMinutes()))
                            .toList();
                    return new PublicBoardResponse(q.getDoctor().getDoctorId(),
                            q.getDoctor().getUser().getFullName(), q.getStatus(),
                            nowServing(rows), entries);
                })
                .toList();
    }

    /* ── Rules shared by the desk and the QR ─────────────────────────────── */

    /**
     * Only today's queue can be worked. Nothing closes a queue at midnight, so
     * without this yesterday's queue would still take walk-ins.
     */
    private void requireToday(Queue queue) {
        if (!queue.getQueueDate().equals(LocalDate.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "QUEUE_NOT_TODAY",
                    "This queue is from another day. Open today's queue instead.");
        }
    }

    /**
     * One active ticket (waiting or in the chair) per patient per day, across
     * every doctor's queue (guide decision 9). Read as a List, so two rows
     * left behind by an old bug still answer 409 rather than crash.
     */
    private void requireNoActiveTicketToday(Patient patient, String who) {
        List<QueueTicket> active =
                ticketRepository.findActiveTicketForPatientOnDate(patient, LocalDate.now());
        if (!active.isEmpty()) {
            String doctor = active.get(0).getQueue().getDoctor().getUser().getFullName();
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_IN_QUEUE",
                    who + " already in " + doctor + "'s queue today.");
        }
    }

    /** The next number in this queue. Call only while holding the queue's lock. */
    private QueueTicket issueTicket(Queue queue, Patient patient, MedicalService service) {
        Integer highest = ticketRepository.highestTicketNumber(queue);
        QueueTicket ticket = new QueueTicket(queue, patient, service,
                highest == null ? 1 : highest + 1);
        return ticketRepository.save(ticket);
    }

    private Patient signedInPatient(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "Please sign in again."));
        return patientRepository.findByUser(user)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "NOT_A_PATIENT",
                        "Only patients can join the queue this way. The desk adds walk-ins."));
    }

    /** Found by phone plus name, because a family shares one number. */
    private Patient walkInPatient(JoinQueueDTO request) {
        String fullName = trimmed(request.getFullName());
        String phone = PhoneNumbers.normalise(request.getPhoneNumber());
        if (fullName == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NAME_REQUIRED",
                    "Please enter your full name.");
        }
        if (!PhoneNumbers.isValidLebanese(phone)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHONE_INVALID",
                    "Please enter a valid Lebanese phone number.");
        }
        return patientRepository.findByPhoneNumberAndFullName(phone, fullName)
                .orElseGet(() -> patientRepository.save(new Patient(fullName, phone)));
    }

    /**
     * One ticket as its own patient sees it: position and wait come from the
     * same board the desk reads, so the phone and the desk never disagree.
     */
    private PatientTicketResponse patientView(QueueTicket ticket, boolean withAccessCode) {
        Queue queue = ticket.getQueue();
        List<QueueTicketResponse> rows = board(queue);
        QueueTicketResponse row = rows.stream()
                .filter(r -> r.getTicketId().equals(ticket.getTicketId()))
                .findFirst()
                .orElseThrow();
        return new PatientTicketResponse(ticket.getTicketId(), row.getTicket(), row.getStatus(),
                row.getPosition(), row.getEtaMinutes(), queue.getDoctor().getUser().getFullName(),
                ticket.getMedicalService().getServiceName(), nowServing(rows),
                queue.getQueueDate().toString(), withAccessCode ? ticket.getAccessCode() : null);
    }

    private static String nowServing(List<QueueTicketResponse> rows) {
        return rows.stream()
                .filter(r -> IN_CHAIR.equals(r.getStatus()))
                .map(QueueTicketResponse::getTicket)
                .findFirst()
                .orElse(null);
    }

    /**
     * How long someone joining now would wait: what is left of the treatment
     * in the chair, plus every treatment still waiting. The same sum the board
     * gives the last person in line, carried one place further.
     */
    private int waitForSomeoneJoiningNow(Queue queue) {
        List<QueueTicket> tickets = ticketRepository.findByQueue(queue);
        int inChair = tickets.stream()
                .filter(t -> IN_CHAIR.equals(t.getStatus()))
                .findFirst()
                .map(QueueService::minutesRemaining)
                .orElse(0);
        int waiting = tickets.stream()
                .filter(t -> WAITING.equals(t.getStatus()))
                .mapToInt(t -> t.getMedicalService().getDurationMinutes())
                .sum();
        return inChair + waiting;
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
    /**
     * One doctor's label prefix. Public so the doctor's own schedule shows the
     * same label the patient and the desk see - the prefix rule must live in
     * exactly one place or the same ticket reads K-1 on one screen and R-1 on
     * another.
     */
    public String ticketPrefixFor(Doctor doctor) {
        return ticketPrefixes().getOrDefault(doctor.getDoctorId(), "R");
    }

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
