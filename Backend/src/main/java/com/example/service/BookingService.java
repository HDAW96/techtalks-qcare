package com.example.service;

import com.example.dto.AppointmentBookingDTO;
import com.example.dto.AppointmentResponse;
import com.example.dto.DayAvailabilityResponse;
import com.example.dto.DeskAppointmentResponse;
import com.example.entity.Appointment;
import com.example.entity.Doctor;
import com.example.entity.DoctorAvailability;
import com.example.entity.MedicalService;
import com.example.entity.Patient;
import com.example.entity.User;
import com.example.repository.AppointmentRepository;
import com.example.repository.DoctorAvailabilityRepository;
import com.example.repository.DoctorLeaveRepository;
import com.example.repository.DoctorRepository;
import com.example.repository.MedicalServiceRepository;
import com.example.repository.PatientRepository;
import com.example.repository.UserRepository;
import com.example.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class BookingService {

    /** The clinic books on the half hour. */
    private static final int GRID_MINUTES = 30;
    private static final DateTimeFormatter LOCAL_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final String ROLE_RECEPTIONIST = "RECEPTIONIST";

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final MedicalServiceRepository serviceRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final DoctorLeaveRepository leaveRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;

    public BookingService(AppointmentRepository appointmentRepository,
            DoctorRepository doctorRepository,
            MedicalServiceRepository serviceRepository,
            DoctorAvailabilityRepository availabilityRepository,
            DoctorLeaveRepository leaveRepository,
            PatientRepository patientRepository,
            UserRepository userRepository) {
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.serviceRepository = serviceRepository;
        this.availabilityRepository = availabilityRepository;
        this.leaveRepository = leaveRepository;
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> forSignedInPatient(Integer userId) {
        return appointmentRepository.findForPatient(patientOf(userId)).stream()
                .map(AppointmentResponse::of)
                .toList();
    }

    /**
     * Every appointment in the clinic on one day, for the reception desk.
     * Patients never reach this: their own list is forSignedInPatient.
     */
    @Transactional(readOnly = true)
    public List<DeskAppointmentResponse> forDay(Integer userId, String date) {
        requireReceptionist(userId);
        LocalDate day;
        try {
            day = date == null ? LocalDate.now() : LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_INVALID",
                    "That is not a valid date.");
        }
        return appointmentRepository
                .findAllBetween(day.atStartOfDay(), day.plusDays(1).atStartOfDay()).stream()
                .map(DeskAppointmentResponse::of)
                .toList();
    }

    /**
     * Every start time on the grid for the next few days, marked open or not.
     *
     * A time is open only if the whole treatment fits inside one of the
     * doctor's working windows and overlaps nothing already booked. Checking
     * the end as well as the start is what stops a 45 minute cleaning being
     * booked at 12:45 when the clinic closes for lunch at 13:00.
     *
     * When an appointment is being moved, `ignoringId` names it so its
     * current time does not block the times around it. Only someone allowed
     * to move it may pass it (its patient, or reception): otherwise anyone
     * could learn when someone else is booked.
     */
    @Transactional(readOnly = true)
    public List<DayAvailabilityResponse> availability(Integer doctorId, Integer serviceId, int days,
            Integer userId, Integer ignoringId) {
        if (ignoringId != null) {
            if (userId == null) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "Please sign in again.");
            }
            movableAppointment(userId, ignoringId);
        }
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DOCTOR_NOT_FOUND",
                        "That doctor was not found."));
        MedicalService service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND",
                        "That treatment was not found."));

        int duration = service.getDurationMinutes();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        List<DayAvailabilityResponse> result = new ArrayList<>();

        for (int offset = 0; offset < days; offset++) {
            LocalDate date = today.plusDays(offset);
            List<DoctorAvailability> windows = availabilityRepository
                    .findByDoctorAndDayOfWeek(doctor, date.getDayOfWeek().getValue());

            // A day the doctor is on leave is shown exactly like a
            // non-clinic day (an empty slot list) - the day strip already
            // reads an empty list as "Not working" rather than "Full"
            // (see SlotPicker.jsx), so leave needs no new label of its own.
            boolean onLeave = leaveRepository.existsForDoctorOnDate(doctor, date);
            if (windows.isEmpty() || onLeave) {
                // The doctor does not work this weekday at all, or is away.
                result.add(new DayAvailabilityResponse(date.toString(), List.of()));
                continue;
            }

            List<Appointment> taken = takenForDay(doctor, date, ignoringId);

            List<DayAvailabilityResponse.Slot> slots = new ArrayList<>();
            for (LocalTime start : gridTimes(windows)) {
                boolean fits = fitsWorkingHours(windows, start, duration);
                boolean free = taken.stream().noneMatch(a -> overlaps(
                        date.atTime(start), duration,
                        a.getAppointmentDatetime(), a.getMedicalService().getDurationMinutes()));
                boolean future = date.atTime(start).isAfter(now);
                slots.add(new DayAvailabilityResponse.Slot(start.toString(), fits && free && future));
            }
            result.add(new DayAvailabilityResponse(date.toString(), slots));
        }
        return result;
    }

    // READ_COMMITTED: see requireBookable for why the default isolation lets
    // two bookings for the same time both through.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse book(Integer userId, AppointmentBookingDTO request) {
        Patient patient = patientOf(userId);

        if (request.getDoctorId() == null || request.getServiceId() == null
                || request.getDatetime() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BOOKING_INCOMPLETE",
                    "Please choose a doctor, a treatment and a time.");
        }
        Doctor doctor = doctorRepository.findById(request.getDoctorId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DOCTOR_NOT_FOUND",
                        "That doctor was not found."));
        MedicalService service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND",
                        "That treatment was not found."));

        LocalDateTime when = parseDatetime(request.getDatetime());
        requireBookable(doctor, service, when, null);

        Appointment appointment = new Appointment(patient, doctor, when, service);
        appointmentRepository.save(appointment);
        return AppointmentResponse.of(appointment);
    }

    /**
     * Moves an appointment to a new time with the same doctor and treatment.
     * A patient may move only their own; reception may move anyone's. The new
     * time passes exactly the checks a fresh booking does.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse reschedule(Integer userId, Integer appointmentId, String datetime) {
        Appointment appointment = movableAppointment(userId, appointmentId);

        if (!"BOOKED".equals(appointment.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_RESCHEDULABLE",
                    "Only a booked appointment can be moved.");
        }
        if (datetime == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BOOKING_INCOMPLETE",
                    "Please choose a new time.");
        }
        LocalDateTime when = parseDatetime(datetime);
        if (when.equals(appointment.getAppointmentDatetime())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SAME_TIME",
                    "That is already the time of this appointment.");
        }

        requireBookable(appointment.getDoctor(), appointment.getMedicalService(), when,
                appointment.getAppointmentId());

        appointment.setAppointmentDatetime(when);
        appointmentRepository.save(appointment);
        return AppointmentResponse.of(appointment);
    }

    /**
     * Cancels an appointment: a patient's own, or anyone's when the caller is
     * reception - the same rule as reschedule. The record is kept and marked
     * CANCELLED rather than deleted: it belongs in the patient's history, and
     * the clinic wants to know who cancels. The slot is free again at once,
     * because availability only counts BOOKED.
     */
    @Transactional
    public AppointmentResponse cancel(Integer userId, Integer appointmentId) {
        Appointment appointment = movableAppointment(userId, appointmentId);

        if (!"BOOKED".equals(appointment.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_CANCELLABLE",
                    "Only a booked appointment can be cancelled.");
        }

        appointment.setStatus("CANCELLED");
        appointmentRepository.save(appointment);
        return AppointmentResponse.of(appointment);
    }

    /**
     * The appointment, if the caller may move it: reception may move any, a
     * patient only their own. Everyone else gets what ownedAppointment gives.
     */
    private Appointment movableAppointment(Integer userId, Integer appointmentId) {
        if (isReceptionist(userId)) {
            return appointmentRepository.findById(appointmentId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                            "APPOINTMENT_NOT_FOUND", "That appointment was not found."));
        }
        return ownedAppointment(userId, appointmentId);
    }

    /**
     * The role is read from the database, not the token, so an account whose
     * role changes loses access straight away rather than when its token ends.
     */
    private boolean isReceptionist(Integer userId) {
        return userRepository.findById(userId)
                .map(u -> ROLE_RECEPTIONIST.equals(u.getRole()))
                .orElse(false);
    }

    private void requireReceptionist(Integer userId) {
        if (!isReceptionist(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_RECEPTION",
                    "Only reception staff can see the clinic's appointments.");
        }
    }

    /**
     * The appointment, but only if it is the caller's. Someone else's answers
     * the same as one that does not exist, so ids cannot be probed.
     */
    private Appointment ownedAppointment(Integer userId, Integer appointmentId) {
        Patient patient = patientOf(userId);
        return appointmentRepository.findById(appointmentId)
                .filter(a -> a.getPatient().getPatientId().equals(patient.getPatientId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND",
                        "That appointment was not found."));
    }

    private LocalDateTime parseDatetime(String datetime) {
        try {
            return LocalDateTime.parse(datetime, LOCAL_DATETIME);
        } catch (DateTimeParseException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATETIME_INVALID",
                    "That is not a valid date and time.");
        }
    }

    /**
     * Refuses a time that has passed, falls outside the doctor's hours, or
     * overlaps another booking. `ignoringId` is the appointment being moved:
     * it must not count as clashing with its own new time.
     *
     * Call it only inside a write transaction, right before saving.
     */
    private void requireBookable(Doctor doctor, MedicalService service, LocalDateTime when,
            Integer ignoringId) {
        if (!when.isAfter(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATETIME_PAST",
                    "That time has already passed. Please choose another.");
        }

        // Checked before working hours on purpose: a doctor on leave that
        // day is the more useful reason to show a patient, rather than
        // "outside hours" for a day that would otherwise have been open.
        if (leaveRepository.existsForDoctorOnDate(doctor, when.toLocalDate())) {
            throw new ApiException(HttpStatus.CONFLICT, "DOCTOR_ON_LEAVE",
                    "This doctor is on leave that day. Please choose another date.");
        }

        int duration = service.getDurationMinutes();
        List<DoctorAvailability> windows = availabilityRepository
                .findByDoctorAndDayOfWeek(doctor, when.getDayOfWeek().getValue());
        if (!fitsWorkingHours(windows, when.toLocalTime(), duration)) {
            throw new ApiException(HttpStatus.CONFLICT, "OUTSIDE_HOURS",
                    "That doctor is not available then.");
        }

        // The database cannot prevent this on its own. A unique key on
        // (doctor, datetime) would only catch two bookings starting at the same
        // minute, but treatments have length: a 45 minute cleaning at 10:00
        // also blocks 10:30.
        //
        // Locking the doctor's row first makes the check and the save that
        // follows it one step: a second booking for this doctor waits here
        // until the first one's transaction ends, then sees its appointment.
        // Without it, two patients clicking the same time at once could both
        // pass the check.
        //
        // The lock alone is not enough on MySQL. Its default isolation
        // (REPEATABLE READ) reads from a snapshot taken at the transaction's
        // first query, so the waiting booking would still not see the one it
        // waited for. Both callers therefore run READ_COMMITTED, where the
        // query below sees everything committed before it runs.
        doctorRepository.lockById(doctor.getDoctorId());
        LocalDate date = when.toLocalDate();
        boolean clash = takenForDay(doctor, date, ignoringId).stream()
                .anyMatch(a -> overlaps(when, duration,
                        a.getAppointmentDatetime(), a.getMedicalService().getDurationMinutes()));
        if (clash) {
            throw new ApiException(HttpStatus.CONFLICT, "SLOT_TAKEN",
                    "That time has just been taken. Please choose another.");
        }
    }

    private List<Appointment> takenForDay(Doctor doctor, LocalDate date, Integer ignoringId) {
        return appointmentRepository
                .findBookedForDoctorBetween(doctor, date.atStartOfDay(), date.plusDays(1).atStartOfDay())
                .stream()
                .filter(a -> !a.getAppointmentId().equals(ignoringId))
                .toList();
    }

    /** Half-hour starts covering every working window of the day. */
    private List<LocalTime> gridTimes(List<DoctorAvailability> windows) {
        List<LocalTime> times = new ArrayList<>();
        for (DoctorAvailability window : windows) {
            for (LocalTime t = window.getStartTime();
                    t.isBefore(window.getEndTime());
                    t = t.plusMinutes(GRID_MINUTES)) {
                if (!times.contains(t)) {
                    times.add(t);
                }
                // LocalTime wraps at midnight: 23:30 plus 30 minutes is 00:00,
                // which is "before" a window ending at 23:59 and would loop forever.
                if (t.plusMinutes(GRID_MINUTES).isBefore(t)) {
                    break;
                }
            }
        }
        times.sort(LocalTime::compareTo);
        return times;
    }

    /**
     * The whole treatment fits inside one working window. Checking the end as
     * well as the start is what stops a 45 minute cleaning at 12:45 when the
     * clinic closes at 13:00. A treatment that would run past midnight never
     * fits: LocalTime wraps, so 23:30 plus 45 minutes is 00:15, which would
     * otherwise pass as "not after" the window's end.
     */
    private boolean fitsWorkingHours(List<DoctorAvailability> windows, LocalTime start,
            int duration) {
        LocalTime end = start.plusMinutes(duration);
        if (!end.isAfter(start)) {
            return false;
        }
        return windows.stream().anyMatch(
                w -> !start.isBefore(w.getStartTime()) && !end.isAfter(w.getEndTime()));
    }

    /** Two appointments clash unless one finishes before the other starts. */
    private boolean overlaps(LocalDateTime startA, int minutesA,
            LocalDateTime startB, int minutesB) {
        return startA.isBefore(startB.plusMinutes(minutesB))
                && startB.isBefore(startA.plusMinutes(minutesA));
    }

    private Patient patientOf(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "Please sign in again."));
        return patientRepository.findByUser(user)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "NOT_A_PATIENT",
                        "Only patients can book appointments."));
    }

    @Transactional
    public AppointmentResponse complete(Integer userId, Integer appointmentId, String clinicalNotes) {

    // Get the appointment
    Appointment appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow(() -> new ApiException(
                    HttpStatus.NOT_FOUND,
                    "APPOINTMENT_NOT_FOUND",
                    "Appointment not found."
            ));

    // Make sure the signed-in user is a doctor
    User user = userRepository.findById(userId).orElseThrow(() -> new ApiException(
                    HttpStatus.NOT_FOUND,
                    "USER_NOT_FOUND",
                    "User not found."
            ));

    Doctor doctor = doctorRepository.findByUser(user)
            .orElseThrow(() -> new ApiException(
                    HttpStatus.FORBIDDEN,
                    "NOT_A_DOCTOR",
                    "Only doctors can complete appointments."
            ));

    // Make sure this appointment belongs to this doctor
    if (!appointment.getDoctor().getDoctorId().equals(doctor.getDoctorId())) {
        throw new ApiException(
                HttpStatus.FORBIDDEN,
                "NOT_YOUR_APPOINTMENT",
                "You can only complete your own appointments."
        );
    }

    // Only BOOKED appointments can be completed
    if (!"BOOKED".equals(appointment.getStatus())) {
        throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_APPOINTMENT_STATUS",
                "Only booked appointments can be completed."
        );
    }

    // Complete the appointment
    appointment.setStatus("COMPLETED");
    appointment.setClinicalNotes(clinicalNotes);

    appointmentRepository.save(appointment);

    return AppointmentResponse.of(appointment);
}
}
