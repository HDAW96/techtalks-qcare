package com.example.service;

import com.example.dto.AnalyticsResponse;
import com.example.dto.AnalyticsResponse.AppointmentTotals;
import com.example.dto.AnalyticsResponse.DoctorPatients;
import com.example.dto.AnalyticsResponse.HourCount;
import com.example.dto.AnalyticsResponse.ServiceCount;
import com.example.entity.Appointment;
import com.example.entity.Doctor;
import com.example.entity.MedicalService;
import com.example.entity.QueueTicket;
import com.example.repository.AppointmentRepository;
import com.example.repository.DoctorRepository;
import com.example.repository.QueueTicketRepository;
import com.example.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The admin's numbers for a date range. It is one clinic, so the rows for a
 * range are few: they are loaded and counted here rather than in SQL.
 *
 * Read-only transaction because appointments, tickets and queues reach
 * doctors, users and services lazily.
 */
@Service
public class AnalyticsService {

    /** 30 days counting both ends. */
    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 366;

    private final AppointmentRepository appointmentRepository;
    private final QueueTicketRepository ticketRepository;
    private final DoctorRepository doctorRepository;

    public AnalyticsService(AppointmentRepository appointmentRepository,
            QueueTicketRepository ticketRepository,
            DoctorRepository doctorRepository) {
        this.appointmentRepository = appointmentRepository;
        this.ticketRepository = ticketRepository;
        this.doctorRepository = doctorRepository;
    }

    /**
     * `from` and `to` are dates like 2026-09-01, both inclusive. Either may
     * be missing: `to` defaults to today and `from` to 29 days before `to`.
     */
    @Transactional(readOnly = true)
    public AnalyticsResponse analytics(String fromParam, String toParam) {
        LocalDate parsedFrom = parseDate(fromParam, "start");
        LocalDate parsedTo = parseDate(toParam, "end");
        LocalDate to = parsedTo != null ? parsedTo : LocalDate.now();
        LocalDate from = parsedFrom != null ? parsedFrom : to.minusDays(DEFAULT_DAYS - 1);

        if (from.isAfter(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RANGE_INVALID",
                    "The start date must be on or before the end date.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RANGE_TOO_LONG",
                    "Please choose a range of at most " + MAX_DAYS + " days.");
        }

        // The whole of the last day: everything before the next day's midnight.
        List<Appointment> appointments = appointmentRepository
                .findByAppointmentDatetimeGreaterThanEqualAndAppointmentDatetimeLessThan(
                        from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        List<QueueTicket> tickets = ticketRepository.findByQueueQueueDateBetween(from, to);

        List<Appointment> kept = appointments.stream()
                .filter(a -> !"CANCELLED".equals(a.getStatus()))
                .toList();
        List<QueueTicket> walkIns = walkIns(tickets, kept);

        return new AnalyticsResponse(
                from.toString(),
                to.toString(),
                appointmentTotals(appointments),
                walkIns.size(),
                averageWaitMinutes(tickets),
                patientsPerDoctor(appointments, tickets),
                busiestServices(kept, walkIns),
                busiestHours(kept, walkIns));
    }

    private static LocalDate parseDate(String value, String which) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RANGE_INVALID",
                    "The " + which + " date is not a valid date.");
        }
    }

    /**
     * There is no no-show status: a booking still BOOKED once its time has
     * passed is counted as a no-show. A visit the doctor forgot to complete
     * looks the same.
     */
    private static AppointmentTotals appointmentTotals(List<Appointment> appointments) {
        LocalDateTime now = LocalDateTime.now();
        long completed = 0;
        long cancelled = 0;
        long noShow = 0;
        long upcoming = 0;
        for (Appointment a : appointments) {
            switch (a.getStatus()) {
                case "COMPLETED" -> completed++;
                case "CANCELLED" -> cancelled++;
                default -> {
                    if (a.getAppointmentDatetime().isAfter(now)) {
                        upcoming++;
                    } else {
                        noShow++;
                    }
                }
            }
        }
        return new AppointmentTotals(appointments.size(), completed, cancelled, noShow, upcoming);
    }

    /**
     * Tickets are never linked to the appointment they came from, so "no
     * appointment" alone would count a booked patient who got a ticket at the
     * desk. A walk-in is a ticket with no appointment whose patient had no
     * kept booking with that doctor on that day. A skipped walk-in still
     * counts: they did walk in.
     */
    private static List<QueueTicket> walkIns(List<QueueTicket> tickets, List<Appointment> kept) {
        Set<String> booked = new HashSet<>();
        for (Appointment a : kept) {
            booked.add(visitKey(a.getPatient().getPatientId(), a.getDoctor().getDoctorId(),
                    a.getAppointmentDatetime().toLocalDate()));
        }
        return tickets.stream()
                .filter(t -> t.getAppointment() == null)
                .filter(t -> !booked.contains(visitKey(t.getPatient().getPatientId(),
                        t.getQueue().getDoctor().getDoctorId(), t.getQueue().getQueueDate())))
                .toList();
    }

    private static String visitKey(Integer patientId, Integer doctorId, LocalDate day) {
        return patientId + "|" + doctorId + "|" + day;
    }

    /** From joining the queue to being called in, over tickets actually called in. */
    private static Long averageWaitMinutes(List<QueueTicket> tickets) {
        List<QueueTicket> calledIn = tickets.stream()
                .filter(t -> t.getStartedAt() != null && t.getJoinedAt() != null)
                .toList();
        if (calledIn.isEmpty()) {
            return null;
        }
        double averageSeconds = calledIn.stream()
                .mapToLong(t -> Duration.between(t.getJoinedAt(), t.getStartedAt()).toSeconds())
                .average()
                .orElse(0);
        return Math.round(averageSeconds / 60.0);
    }

    /** Distinct patients seen - a completed appointment or a finished ticket - for every doctor, even with 0. */
    private List<DoctorPatients> patientsPerDoctor(List<Appointment> appointments, List<QueueTicket> tickets) {
        Map<Integer, Set<Integer>> seen = new HashMap<>();
        for (Appointment a : appointments) {
            if ("COMPLETED".equals(a.getStatus())) {
                seen.computeIfAbsent(a.getDoctor().getDoctorId(), id -> new HashSet<>())
                        .add(a.getPatient().getPatientId());
            }
        }
        for (QueueTicket t : tickets) {
            if ("DONE".equals(t.getStatus())) {
                seen.computeIfAbsent(t.getQueue().getDoctor().getDoctorId(), id -> new HashSet<>())
                        .add(t.getPatient().getPatientId());
            }
        }
        return doctorRepository.findAll().stream()
                .map(d -> new DoctorPatients(d.getDoctorId(), doctorName(d),
                        seen.getOrDefault(d.getDoctorId(), Set.of()).size()))
                .sorted(Comparator.comparingLong(DoctorPatients::getPatients).reversed()
                        .thenComparing(DoctorPatients::getDoctorName,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    private static String doctorName(Doctor doctor) {
        return doctor.getUser() != null ? doctor.getUser().getFullName() : null;
    }

    /** Kept appointments plus walk-ins, by treatment - inactive ones included, it is history. */
    private static List<ServiceCount> busiestServices(List<Appointment> kept, List<QueueTicket> walkIns) {
        Map<Integer, MedicalService> services = new LinkedHashMap<>();
        Map<Integer, Long> counts = new HashMap<>();
        for (Appointment a : kept) {
            count(a.getMedicalService(), services, counts);
        }
        for (QueueTicket t : walkIns) {
            count(t.getMedicalService(), services, counts);
        }
        return services.values().stream()
                .map(s -> new ServiceCount(s.getServiceId(), s.getServiceName(), counts.get(s.getServiceId())))
                .sorted(Comparator.comparingLong(ServiceCount::getCount).reversed()
                        .thenComparing(ServiceCount::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static void count(MedicalService service, Map<Integer, MedicalService> services,
            Map<Integer, Long> counts) {
        if (service == null) {
            return;
        }
        services.putIfAbsent(service.getServiceId(), service);
        counts.merge(service.getServiceId(), 1L, Long::sum);
    }

    /** Only hours with at least one visit, in clock order; the page fills the gaps. */
    private static List<HourCount> busiestHours(List<Appointment> kept, List<QueueTicket> walkIns) {
        Map<Integer, Long> byHour = new TreeMap<>();
        for (Appointment a : kept) {
            byHour.merge(a.getAppointmentDatetime().getHour(), 1L, Long::sum);
        }
        for (QueueTicket t : walkIns) {
            if (t.getJoinedAt() != null) {
                byHour.merge(t.getJoinedAt().getHour(), 1L, Long::sum);
            }
        }
        return byHour.entrySet().stream()
                .map(e -> new HourCount(e.getKey(), e.getValue()))
                .toList();
    }
}
