package com.example.service;

import com.example.entity.Admin;
import com.example.entity.Doctor;
import com.example.entity.DoctorAvailability;
import com.example.entity.MedicalService;
import com.example.entity.Receptionist;
import com.example.entity.User;
import com.example.repository.AdminRepository;
import com.example.repository.DoctorAvailabilityRepository;
import com.example.repository.DoctorRepository;
import com.example.repository.MedicalServiceRepository;
import com.example.repository.ReceptionistRepository;
import com.example.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

/**
 * Puts the clinic's doctors, treatments, receptionists and admin into an empty
 * database so the app has something to show. The doctor block runs only when
 * there are no doctors yet, so it never overwrites real data. The
 * receptionists and the admin have their own checks, because every existing
 * development database already has doctors and would otherwise skip them.
 *
 * The doctors and services deliberately mirror the frontend's mock data, so a
 * screen looks the same before and after it is wired to the backend and any
 * difference is a real bug rather than different test data.
 *
 * The seeded accounts all use qcare.seed.password (QCARE_SEED_PASSWORD) and are
 * flagged must_change_password, which the server enforces: until each account
 * chooses its own password, it can do nothing but change it. The default,
 * changeme123, is for a development database only; ProductionSecretsCheck
 * refuses to start a production server that still uses it.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final AdminRepository adminRepository;
    private final MedicalServiceRepository serviceRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final ReceptionistRepository receptionistRepository;
    private final PasswordEncoder passwordEncoder;
    private final String seedPassword;

    public DataSeeder(UserRepository userRepository,
            DoctorRepository doctorRepository,
            AdminRepository adminRepository,
            MedicalServiceRepository serviceRepository,
            DoctorAvailabilityRepository availabilityRepository,
            ReceptionistRepository receptionistRepository,
            PasswordEncoder passwordEncoder,
            @Value("${qcare.seed.password}") String seedPassword) {
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.serviceRepository = serviceRepository;
        this.availabilityRepository = availabilityRepository;
        this.adminRepository = adminRepository;
        this.receptionistRepository = receptionistRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedPassword = seedPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (serviceRepository.count() == 0) {
            serviceRepository.saveAll(List.of(
                    new MedicalService("Consultation", 20),
                    new MedicalService("Cleaning and scaling", 45),
                    new MedicalService("Filling", 40),
                    new MedicalService("Extraction", 30)));
            log.info("Seeded {} services", serviceRepository.count());
        }

        // Checked on users rather than the admin table: users is where the
        // (phone, name) constraint lives, so this can never insert a duplicate.
        if (!userRepository.existsByRole("ADMIN")) {
            seedAdmin("Admin One", "03400011");
            log.info("Seeded {} admins", adminRepository.count());
        }

        if (doctorRepository.count() == 0) {
            Doctor haddad = seedDoctor("Dr. Rania Haddad", "03400001", "General dentistry");
            Doctor fares = seedDoctor("Dr. Karim Fares", "03400002", "Orthodontics");

            // Two windows a day, because the clinic closes over lunch. A
            // treatment must fit inside one window, so nothing can be booked
            // across the break.
            // Dr Haddad: Monday to Friday.
            for (int day = 1; day <= 5; day++) {
                availabilityRepository.save(
                        new DoctorAvailability(haddad, day, LocalTime.of(9, 0), LocalTime.of(13, 0)));
                availabilityRepository.save(
                        new DoctorAvailability(haddad, day, LocalTime.of(14, 0), LocalTime.of(17, 0)));
            }
            // Dr Fares: Tuesday, Thursday and Saturday.
            for (int day : new int[] { 2, 4, 6 }) {
                availabilityRepository.save(
                        new DoctorAvailability(fares, day, LocalTime.of(10, 0), LocalTime.of(13, 0)));
                availabilityRepository.save(
                        new DoctorAvailability(fares, day, LocalTime.of(14, 0), LocalTime.of(18, 0)));
            }
            log.info("Seeded {} doctors with availability", doctorRepository.count());
        }

        // The receptionists are seeded independently of the doctors: the block
        // above is skipped on any database that already has doctors, which is
        // every existing development database.
        if (!userRepository.existsByRole("RECEPTIONIST")) {
            seedReceptionist("Reception Desk One", "03400009");
            seedReceptionist("Reception Desk Two", "03400010");
            log.info("Seeded {} receptionists", receptionistRepository.count());
        }
    }

    private Doctor seedDoctor(String name, String phone, String specialization) {
        User user = new User(name, phone, passwordEncoder.encode(seedPassword), "DOCTOR");
        user.setMustChangePassword(true);
        userRepository.save(user);
        return doctorRepository.save(new Doctor(user, specialization));
    }

    private Receptionist seedReceptionist(String name, String phone) {
        User user = new User(name, phone, passwordEncoder.encode(seedPassword), "RECEPTIONIST");
        user.setMustChangePassword(true);
        userRepository.save(user);

        Receptionist receptionist = new Receptionist();
        receptionist.setUser(user);
        return receptionistRepository.save(receptionist);
    }

    private Admin seedAdmin(String name, String phone) {
        User user = new User(name, phone, passwordEncoder.encode(seedPassword), "ADMIN");
        user.setMustChangePassword(true);
        userRepository.save(user);

        Admin admin = new Admin();
        admin.setUser(user);
        return adminRepository.save(admin);
    }
}
