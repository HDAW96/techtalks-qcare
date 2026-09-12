package com.example.service;

import com.example.dto.BookingRequestDTO;
import com.example.entity.*;
import com.example.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository PatientRepository;
    private final DoctorRepository doctorRepository;
    private final MedicalServiceRepository medicalServiceRepository;

    public BookingService(AppointmentRepository apptRepo, PatientRepository patRepo,
            DoctorRepository docRepo, MedicalServiceRepository medSvcRepo) {
        this.appointmentRepository = apptRepo;
        this.PatientRepository = patRepo;
        this.doctorRepository = docRepo;
        this.medicalServiceRepository = medSvcRepo;
    }

    @Transactional
    public Appointment bookAppointment(BookingRequestDTO dto) {
        // 1. Silent Patient Auto-Registration via the unique composite key
        Patient patient = PatientRepository.findByPhoneNumberAndFullName(dto.getPhoneNumber(), dto.getFullName())
                .orElseGet(() -> PatientRepository.save(new Patient(dto.getFullName(), dto.getPhoneNumber())));

        // 2. Lookups
        Doctor doctor = doctorRepository.findById(dto.getDoctorId())
                .orElseThrow(() -> new IllegalArgumentException("Doctor not found"));

        MedicalService service = medicalServiceRepository.findById(dto.getServiceId())
                .orElseThrow(() -> new IllegalArgumentException("Service not found"));

        // 3. Save Appointment
        Appointment appointment = new Appointment(patient, doctor, dto.getAppointmentDatetime(), service);
        return appointmentRepository.save(appointment);
    }
}