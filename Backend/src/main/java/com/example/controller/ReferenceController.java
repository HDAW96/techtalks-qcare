package com.example.controller;

import com.example.dto.DoctorResponse;
import com.example.dto.ServiceResponse;
import com.example.repository.DoctorRepository;
import com.example.repository.MedicalServiceRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The lists the booking screen needs before anyone signs in: which doctors
 * work here and what they treat. Public on purpose - a patient chooses a
 * doctor before creating an account.
 */
@RestController
@RequestMapping("/api")
public class ReferenceController {

    private final DoctorRepository doctorRepository;
    private final MedicalServiceRepository serviceRepository;

    public ReferenceController(DoctorRepository doctorRepository,
            MedicalServiceRepository serviceRepository) {
        this.doctorRepository = doctorRepository;
        this.serviceRepository = serviceRepository;
    }

    @GetMapping("/doctors")
    public List<DoctorResponse> doctors() {
        return doctorRepository.findAll().stream().map(DoctorResponse::of).toList();
    }

    @GetMapping("/services")
    public List<ServiceResponse> services() {
        // Active only: a deactivated treatment must not be offered for a new
        // booking or walk-in, though old appointments still show it.
        return serviceRepository.findByActiveTrueOrderByServiceNameAsc().stream()
                .map(ServiceResponse::of).toList();
    }
}
