package com.example.service;

import com.example.entity.Doctor;
import com.example.entity.DoctorAvailability;
import com.example.entity.MedicalService;
import com.example.entity.User;
import com.example.repository.DoctorAvailabilityRepository;
import com.example.repository.DoctorRepository;
import com.example.repository.MedicalServiceRepository;
import com.example.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

/**
 * Puts the clinic's doctors and treatments into an empty database so the app
 * has something to show. It runs only when there are no doctors yet, so it
 * never overwrites real data.
 *
 * The doctors and services deliberately mirror the frontend's mock data, so a
 * screen looks the same before and after it is wired to the backend and any
 * difference is a real bug rather than different test data.
 *
 * The seeded accounts use a known password and are flagged must_change_password.
 * That is fine for a development database and must not be relied on anywhere else.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String SEED_PASSWORD = "changeme123";

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final MedicalServiceRepository serviceRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository,
            DoctorRepository doctorRepository,
            MedicalServiceRepository serviceRepository,
            DoctorAvailabilityRepository availabilityRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.serviceRepository = serviceRepository;
        this.availabilityRepository = availabilityRepository;
        this.passwordEncoder = passwordEncoder;
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
    }

    private Doctor seedDoctor(String name, String phone, String specialization) {
        User user = new User(name, phone, passwordEncoder.encode(SEED_PASSWORD), "DOCTOR");
        user.setMustChangePassword(true);
        userRepository.save(user);
        return doctorRepository.save(new Doctor(user, specialization));
    }
}
