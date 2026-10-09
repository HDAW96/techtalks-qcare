package com.example.service;

import com.example.dto.PatientSearchResponse;
import com.example.repository.PatientRepository;
import com.example.util.PhoneNumbers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PatientSearchService {

    private final PatientRepository patientRepository;

    public PatientSearchService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    @Transactional(readOnly = true)
    public List<PatientSearchResponse> search(String search) {
        String text = search == null || search.isBlank() ? null : search.trim();
        if (PhoneNumbers.isPhoneSearch(text)) {
            text = PhoneNumbers.searchFragment(text);
        }

        return patientRepository.searchPatients(text == null ? null : likeEscaped(text))
                .stream()
                .map(PatientSearchResponse::of)
                .toList();
    }

    private static String likeEscaped(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
