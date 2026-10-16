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
         // Return empty list if search is null or blank
        if (search == null || search.isBlank()) {
        return List.of();
    }
     String text = search.trim();
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
