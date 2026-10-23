package com.example.service;

import com.example.entity.Admin;
import com.example.entity.Appointment;
import com.example.entity.Doctor;
import com.example.entity.DoctorAvailability;
import com.example.entity.MedicalService;
import com.example.entity.Patient;
import com.example.entity.Queue;
import com.example.entity.QueueTicket;
import com.example.entity.Receptionist;
import com.example.entity.User;
import com.example.repository.AdminRepository;
import com.example.repository.AppointmentRepository;
import com.example.repository.DoctorAvailabilityRepository;
import com.example.repository.DoctorLeaveRepository;
import com.example.repository.DoctorRepository;
import com.example.repository.MedicalServiceRepository;
import com.example.repository.PatientRepository;
import com.example.repository.QueueRepository;
import com.example.repository.QueueTicketRepository;
import com.example.repository.ReceptionistRepository;
import com.example.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DEMO ONLY. Makes a demo-ready clinic: one known login per role and a day's
 * worth of appointments and queue, anchored to today.
 *
 * It exists only under the "demo" Spring profile
 * (SPRING_PROFILES_ACTIVE=demo), so a normal run and every test other than
 * DemoDataSeederTest never load it. It runs after {@link DataSeeder}, which
 * supplies the services, doctors, receptionists and admin it builds on.
 *
 * Safe to run on every start:
 * - Logins are looked up by (phone, name) and reset: the demo password is
 *   restored and must_change_password cleared, so a changed password is
 *   repaired by a restart. Nothing is inserted twice.
 * - Only data belonging to the reserved demo patients (see DEMO_PATIENTS and
 *   DEMO_PATIENT_PHONE) is deleted and rebuilt, relative to today. Anyone
 *   else's appointments and tickets are left alone.
 *
 * The password is never stored hashed in source: it is encoded at runtime
 * with the application's PasswordEncoder.
 */
@Component
@Profile("demo")
@Order(2)
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    /** Demo-only password. Override with QCARE_DEMO_PASSWORD. Meets the password policy. */
    public static final String DEFAULT_DEMO_PASSWORD = "Demo-Pass1!";

    // Logins. The first four already exist in DataSeeder; they are reset, not duplicated.
    public static final String DESK_NAME = "Reception Desk One";
    public static final String DESK_PHONE = "03400009";
    public static final String HADDAD_NAME = "Dr. Rania Haddad";
    public static final String HADDAD_PHONE = "03400001";
    public static final String FARES_NAME = "Dr. Karim Fares";
    public static final String FARES_PHONE = "03400002";
    public static final String ADMIN_NAME = "Admin One";
    public static final String ADMIN_PHONE = "03400011";
    public static final String DEMO_PATIENT_NAME = "Demo Patient";
    public static final String DEMO_PATIENT_PHONE = "03400020";

    /** Reserved range 03400101-03400108: demo patients with no login. */
    private static final String[][] DEMO_PATIENTS = {
            { "Layla Nasser", "03400101" },
            { "Omar Khoury", "03400102" },
            { "Maya Saad", "03400103" },
            { "Nour Aziz", "03400104" },
            { "Karl Bou Saab", "03400105" },
            { "Sami Tabet", "03400106" },
            { "Hala Mrad", "03400107" },
            { "Elie Rahme", "03400108" },
    };

    private static final Pattern MYSQL_HOST = Pattern.compile("^jdbc:mysql://([^:/?]+)");

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final ReceptionistRepository receptionistRepository;
    private final AdminRepository adminRepository;
    private final PatientRepository patientRepository;
    private final MedicalServiceRepository serviceRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final DoctorLeaveRepository leaveRepository;
    private final AppointmentRepository appointmentRepository;
    private final QueueRepository queueRepository;
    private final QueueTicketRepository ticketRepository;
    private final PasswordEncoder passwordEncoder;
    private final String demoPassword;
    private final String datasourceUrl;
    private final boolean allowRemote;
    private final String environment;

    public DemoDataSeeder(UserRepository userRepository,
            DoctorRepository doctorRepository,
            ReceptionistRepository receptionistRepository,
            AdminRepository adminRepository,
            PatientRepository patientRepository,
            MedicalServiceRepository serviceRepository,
            DoctorAvailabilityRepository availabilityRepository,
            DoctorLeaveRepository leaveRepository,
            AppointmentRepository appointmentRepository,
            QueueRepository queueRepository,
            QueueTicketRepository ticketRepository,
            PasswordEncoder passwordEncoder,
            @Value("${QCARE_DEMO_PASSWORD:" + DEFAULT_DEMO_PASSWORD + "}") String demoPassword,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            @Value("${qcare.demo.allow-remote:false}") boolean allowRemote,
            @Value("${QCARE_ENV:}") String environment) {
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.receptionistRepository = receptionistRepository;
        this.adminRepository = adminRepository;
        this.patientRepository = patientRepository;
        this.serviceRepository = serviceRepository;
        this.availabilityRepository = availabilityRepository;
        this.leaveRepository = leaveRepository;
        this.appointmentRepository = appointmentRepository;
        this.queueRepository = queueRepository;
        this.ticketRepository = ticketRepository;
        this.passwordEncoder = passwordEncoder;
        this.demoPassword = demoPassword;
        this.datasourceUrl = datasourceUrl;
        this.allowRemote = allowRemote;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(String... args) {
        refuseRemoteDatabase();

        Receptionist desk = upsertReceptionist(DESK_NAME, DESK_PHONE);
        Doctor haddad = upsertDoctor(HADDAD_NAME, HADDAD_PHONE, "General dentistry");
        Doctor fares = upsertDoctor(FARES_NAME, FARES_PHONE, "Orthodontics");
        upsertAdmin(ADMIN_NAME, ADMIN_PHONE);
        Patient demoPatient = upsertDemoPatient();

        List<Patient> walkIns = new ArrayList<>();
        for (String[] p : DEMO_PATIENTS) {
            walkIns.add(patientRepository.findByPhoneNumberAndFullName(p[1], p[0])
                    .orElseGet(() -> patientRepository.save(new Patient(p[0], p[1]))));
        }

        LocalDate today = LocalDate.now();
        ensureWindowsOn(haddad, today);
        ensureWindowsOn(fares, today);

        List<Patient> demoPeople = new ArrayList<>(walkIns);
        demoPeople.add(demoPatient);
        clearDemoVisits(demoPeople);

        MedicalService consultation = service("Consultation");
        MedicalService cleaning = service("Cleaning and scaling");
        MedicalService filling = service("Filling");

        seedToday(today, desk, haddad, fares, walkIns, consultation, cleaning, filling);
        seedUpcomingAndPast(today, haddad, fares, demoPatient, walkIns, consultation, cleaning, filling);

        log.info("Demo data ready: 4 role logins, today's appointments and queue, upcoming and past visits");
    }

    // ---- today: appointments, an open queue with tickets -------------------------------------

    private void seedToday(LocalDate today, Receptionist desk, Doctor haddad, Doctor fares,
            List<Patient> w, MedicalService consultation, MedicalService cleaning, MedicalService filling) {
        LocalDateTime now = LocalDateTime.now();

        Queue haddadQueue = openQueue(haddad, desk, today);
        openQueue(fares, desk, today); // open and empty: the desk can add walk-ins

        // Times are on the 30-minute grid and inside both doctors' windows.
        Appointment done = appointment(w.get(0), haddad, today.atTime(10, 0), consultation, "COMPLETED");
        done.setClinicalNotes("Demo: routine check-up, no issues.");
        appointmentRepository.save(done);
        Appointment inChair = appointment(w.get(1), haddad, today.atTime(10, 30), cleaning, "CHECKED_IN");
        appointment(w.get(2), haddad, today.atTime(14, 30), filling, "BOOKED");
        appointment(w.get(3), fares, today.atTime(11, 0), consultation, "BOOKED");
        appointment(w.get(4), fares, today.atTime(15, 0), cleaning, "BOOKED");

        // Tickets in joining order. ticketNumber follows highestTicketNumber + 1, as
        // QueueService.issueTicket does.
        ticket(haddadQueue, w.get(0), consultation, done, "DONE", now.minusMinutes(60), now.minusMinutes(45));
        ticket(haddadQueue, w.get(1), cleaning, inChair, "IN_CHAIR", now.minusMinutes(30), now.minusMinutes(10));
        ticket(haddadQueue, w.get(5), consultation, null, "WAITING", now.minusMinutes(20), null);
        ticket(haddadQueue, w.get(6), filling, null, "WAITING", now.minusMinutes(12), null);
        ticket(haddadQueue, w.get(7), consultation, null, "WAITING", now.minusMinutes(5), null);
    }

    private Queue openQueue(Doctor doctor, Receptionist desk, LocalDate date) {
        Queue queue = queueRepository.findByDoctorAndQueueDate(doctor, date)
                .orElseGet(() -> new Queue(doctor, desk, date));
        queue.setStatus("OPEN");
        queue.setReceptionist(desk);
        return queueRepository.save(queue);
    }

    private void ticket(Queue queue, Patient patient, MedicalService service, Appointment appointment,
            String status, LocalDateTime joinedAt, LocalDateTime startedAt) {
        Integer highest = ticketRepository.highestTicketNumber(queue);
        QueueTicket t = new QueueTicket(queue, patient, service, highest == null ? 1 : highest + 1);
        t.setStatus(status);
        t.setJoinedAt(joinedAt);
        t.setStartedAt(startedAt);
        t.setAppointment(appointment);
        ticketRepository.save(t);
    }

    // ---- upcoming and past ------------------------------------------------------------------

    private void seedUpcomingAndPast(LocalDate today, Doctor haddad, Doctor fares, Patient demoPatient,
            List<Patient> w, MedicalService consultation, MedicalService cleaning, MedicalService filling) {
        // Demo Patient: two upcoming visits and one finished one, so every tab has content.
        upcoming(haddad, today, 1, 0, demoPatient, consultation);
        upcoming(fares, today, 2, 30, demoPatient, cleaning);
        upcoming(haddad, today, 3, 60, w.get(2), filling);
        upcoming(fares, today, 4, 60, w.get(3), consultation);

        Appointment past = appointment(demoPatient, haddad, today.minusDays(7).atTime(10, 0), consultation, "COMPLETED");
        past.setClinicalNotes("Demo: first visit, X-ray taken.");
        appointmentRepository.save(past);
        appointment(demoPatient, fares, today.minusDays(14).atTime(11, 0), cleaning, "CANCELLED");
    }

    /**
     * Books the nth working day after today for this doctor, skipping days without a
     * window or on leave, at the first window's start plus an offset. Looks a week
     * ahead; with no usable day, nothing is booked.
     */
    private void upcoming(Doctor doctor, LocalDate today, int nth, int offsetMinutes, Patient patient,
            MedicalService service) {
        int found = 0;
        for (int i = 1; i <= 10; i++) {
            LocalDate day = today.plusDays(i);
            if (leaveRepository.existsForDoctorOnDate(doctor, day)) {
                continue;
            }
            LocalTime start = availabilityRepository.findByDoctorAndDayOfWeek(doctor, day.getDayOfWeek().getValue())
                    .stream().map(DoctorAvailability::getStartTime).min(LocalTime::compareTo).orElse(null);
            if (start == null) {
                continue;
            }
            if (++found == nth) {
                appointment(patient, doctor, day.atTime(start.plusMinutes(offsetMinutes)), service, "BOOKED");
                return;
            }
        }
        log.warn("No working day found for {} - skipped one upcoming appointment", doctor.getUser().getFullName());
    }

    private Appointment appointment(Patient patient, Doctor doctor, LocalDateTime at, MedicalService service,
            String status) {
        Appointment a = new Appointment(patient, doctor, at, service);
        a.setStatus(status);
        return appointmentRepository.save(a);
    }

    // ---- idempotency ------------------------------------------------------------------------

    /** Removes only the reserved demo patients' tickets and appointments (tickets first: they reference appointments). */
    private void clearDemoVisits(List<Patient> demoPeople) {
        for (Patient p : demoPeople) {
            ticketRepository.deleteAll(ticketRepository.findByPatient(p));
        }
        ticketRepository.flush();
        for (Patient p : demoPeople) {
            appointmentRepository.deleteAll(appointmentRepository.findForPatient(p));
        }
        appointmentRepository.flush();
    }

    /** Gives a doctor a window for today's weekday if the base seed has none (e.g. a Sunday). */
    private void ensureWindowsOn(Doctor doctor, LocalDate day) {
        int dow = day.getDayOfWeek().getValue();
        if (availabilityRepository.findByDoctorAndDayOfWeek(doctor, dow).isEmpty()) {
            availabilityRepository.save(new DoctorAvailability(doctor, dow, LocalTime.of(10, 0), LocalTime.of(13, 0)));
            availabilityRepository.save(new DoctorAvailability(doctor, dow, LocalTime.of(14, 0), LocalTime.of(17, 0)));
        }
    }

    private MedicalService service(String name) {
        List<MedicalService> all = serviceRepository.findAll();
        return all.stream().filter(s -> name.equals(s.getServiceName())).findFirst()
                .orElseGet(() -> all.get(0));
    }

    // ---- logins -----------------------------------------------------------------------------

    private User upsertUser(String name, String phone, String role) {
        User user = userRepository.findByPhoneNumberAndFullName(phone, name)
                .orElseGet(() -> new User(name, phone, "pending", role));
        user.setPasswordHash(passwordEncoder.encode(demoPassword));
        user.setMustChangePassword(false);
        user.setActive(true);
        return userRepository.save(user);
    }

    private Receptionist upsertReceptionist(String name, String phone) {
        User user = upsertUser(name, phone, "RECEPTIONIST");
        return receptionistRepository.findByUser(user).orElseGet(() -> {
            Receptionist r = new Receptionist();
            r.setUser(user);
            return receptionistRepository.save(r);
        });
    }

    private Doctor upsertDoctor(String name, String phone, String specialization) {
        User user = upsertUser(name, phone, "DOCTOR");
        return doctorRepository.findByUser(user)
                .orElseGet(() -> doctorRepository.save(new Doctor(user, specialization)));
    }

    private Admin upsertAdmin(String name, String phone) {
        User user = upsertUser(name, phone, "ADMIN");
        return adminRepository.findByUser(user).orElseGet(() -> {
            Admin a = new Admin();
            a.setUser(user);
            return adminRepository.save(a);
        });
    }

    private Patient upsertDemoPatient() {
        User user = upsertUser(DEMO_PATIENT_NAME, DEMO_PATIENT_PHONE, "PATIENT");
        Patient patient = patientRepository.findByUser(user)
                .or(() -> patientRepository.findByPhoneNumberAndFullName(DEMO_PATIENT_PHONE, DEMO_PATIENT_NAME))
                .orElseGet(() -> new Patient(DEMO_PATIENT_NAME, DEMO_PATIENT_PHONE));
        patient.setUser(user);
        return patientRepository.save(patient);
    }

    // ---- safety -----------------------------------------------------------------------------

    /**
     * Demo data resets real logins to a public password, so refuse to run in
     * production (QCARE_ENV=production, no override) and against a MySQL server
     * that is not this machine (override: qcare.demo.allow-remote=true).
     */
    private void refuseRemoteDatabase() {
        if (environment != null && environment.trim().equalsIgnoreCase("production")) {
            throw new IllegalStateException(
                    "Demo profile refuses to run when QCARE_ENV=production: it would reset real logins to a public password.");
        }
        Matcher m = MYSQL_HOST.matcher(datasourceUrl);
        if (m.find() && !allowRemote) {
            String host = m.group(1);
            if (!host.equals("localhost") && !host.equals("127.0.0.1") && !host.equals("[::1]")) {
                throw new IllegalStateException("Demo profile refuses to seed the non-local database '" + host
                        + "'. Set qcare.demo.allow-remote=true only if you are sure.");
            }
        }
    }
}
