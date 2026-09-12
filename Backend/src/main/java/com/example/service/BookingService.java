package com.example.service;

import com.example.dto.AppointmentBookingDTO;
import com.example.dto.AppointmentResponse;
import com.example.dto.DayAvailabilityResponse;
import com.example.entity.Appointment;
import com.example.entity.Doctor;
import com.example.entity.DoctorAvailability;
import com.example.entity.MedicalService;
import com.example.entity.Patient;
import com.example.entity.User;
import com.example.repository.AppointmentRepository;
import com.example.repository.DoctorAvailabilityRepository;
import com.example.repository.DoctorRepository;
import com.example.repository.MedicalServiceRepository;
import com.example.repository.PatientRepository;
import com.example.repository.UserRepository;
import com.example.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
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

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final MedicalServiceRepository serviceRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;

    public BookingService(AppointmentRepository appointmentRepository,
            DoctorRepository doctorRepository,
            MedicalServiceRepository serviceRepository,
            DoctorAvailabilityRepository availabilityRepository,
            PatientRepository patientRepository,
            UserRepository userRepository) {
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.serviceRepository = serviceRepository;
        this.availabilityRepository = availabilityRepository;
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
     * Every start time on the grid for the next few days, marked open or not.
     *
     * A time is open only if the whole treatment fits inside one of the
     * doctor's working windows and overlaps nothing already booked. Checking
     * the end as well as the start is what stops a 45 minute cleaning being
     * booked at 12:45 when the clinic closes for lunch at 13:00.
     */
    @Transactional(readOnly = true)
    public List<DayAvailabilityResponse> availability(Integer doctorId, Integer serviceId, int days) {
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

            if (windows.isEmpty()) {
                // The doctor does not work this weekday at all.
                result.add(new DayAvailabilityResponse(date.toString(), List.of()));
                continue;
            }

            List<Appointment> taken = appointmentRepository.findBookedForDoctorBetween(
                    doctor, date.atStartOfDay(), date.plusDays(1).atStartOfDay());

            List<DayAvailabilityResponse.Slot> slots = new ArrayList<>();
            for (LocalTime start : gridTimes(windows)) {
                LocalTime end = start.plusMinutes(duration);
                boolean fits = windows.stream().anyMatch(
                        w -> !start.isBefore(w.getStartTime()) && !end.isAfter(w.getEndTime()));
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

    @Transactional
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

        LocalDateTime when;
        try {
            when = LocalDateTime.parse(request.getDatetime(), LOCAL_DATETIME);
        } catch (DateTimeParseException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATETIME_INVALID",
                    "That is not a valid date and time.");
        }
        if (!when.isAfter(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATETIME_PAST",
                    "That time has already passed. Please choose another.");
        }

        int duration = service.getDurationMinutes();
        List<DoctorAvailability> windows = availabilityRepository
                .findByDoctorAndDayOfWeek(doctor, when.getDayOfWeek().getValue());
        LocalTime start = when.toLocalTime();
        LocalTime end = start.plusMinutes(duration);
        boolean fits = windows.stream().anyMatch(
                w -> !start.isBefore(w.getStartTime()) && !end.isAfter(w.getEndTime()));
        if (!fits) {
            throw new ApiException(HttpStatus.CONFLICT, "OUTSIDE_HOURS",
                    "That doctor is not available then.");
        }

        // The database cannot prevent this on its own. A unique key on
        // (doctor, datetime) would only catch two bookings starting at the same
        // minute, but treatments have length: a 45 minute cleaning at 10:00
        // also blocks 10:30.
        LocalDate date = when.toLocalDate();
        boolean clash = appointmentRepository
                .findBookedForDoctorBetween(doctor, date.atStartOfDay(), date.plusDays(1).atStartOfDay())
                .stream()
                .anyMatch(a -> overlaps(when, duration,
                        a.getAppointmentDatetime(), a.getMedicalService().getDurationMinutes()));
        if (clash) {
            throw new ApiException(HttpStatus.CONFLICT, "SLOT_TAKEN",
                    "That time has just been taken. Please choose another.");
        }

        Appointment appointment = new Appointment(patient, doctor, when, service);
        appointmentRepository.save(appointment);
        return AppointmentResponse.of(appointment);
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
            }
        }
        times.sort(LocalTime::compareTo);
        return times;
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
}
