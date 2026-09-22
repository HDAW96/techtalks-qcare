package com.example.service;

import com.example.dto.PatientHistoryDTO;
import com.example.entity.Patient;
import com.example.repository.AppointmentRepository;
import com.example.repository.PatientRepository;
import com.example.repository.UserRepository;
import com.example.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A patient's past treatments, for the doctor seeing them. Any doctor in the
 * clinic may read any patient's history: the next doctor needs to know what
 * the last one did.
 */
@Service
public class PatientHistoryService {

    private static final String ROLE_DOCTOR = "DOCTOR";

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;

    public PatientHistoryService(UserRepository userRepository,
            PatientRepository patientRepository,
            AppointmentRepository appointmentRepository) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional(readOnly = true)
    public PatientHistoryDTO history(Integer userId, Integer patientId) {
        requireDoctor(userId);
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PATIENT_NOT_FOUND",
                        "That patient was not found."));
        return PatientHistoryDTO.of(patient, appointmentRepository.findCompletedForPatient(patient));
    }

    /**
     * The role is read from the database, not the token, so an account whose
     * role changes loses access straight away rather than when its token ends.
     */
    private void requireDoctor(Integer userId) {
        boolean doctor = userRepository.findById(userId)
                .map(u -> ROLE_DOCTOR.equals(u.getRole()))
                .orElse(false);
        if (!doctor) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_A_DOCTOR",
                    "Only doctors can see a patient's treatment history.");
        }
    }
}
