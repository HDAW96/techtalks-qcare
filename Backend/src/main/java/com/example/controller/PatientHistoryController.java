package com.example.controller;

import com.example.dto.PatientHistoryDTO;
import com.example.service.PatientHistoryService;
import com.example.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kept apart from the doctor's own schedule endpoints (SCRUM-65) so the two
 * can be built side by side without editing the same file.
 */
@RestController
@RequestMapping("/api/doctors/patients")
public class PatientHistoryController {

    private final PatientHistoryService historyService;

    public PatientHistoryController(PatientHistoryService historyService) {
        this.historyService = historyService;
    }

    /** A patient's finished treatments, most recent first. Doctors only. */
    @GetMapping("/{patientId}/history")
    public PatientHistoryDTO history(Authentication authentication, @PathVariable Integer patientId) {
        return historyService.history(signedInUserId(authentication), patientId);
    }

    /**
     * The user id comes from the verified token, never from the request, so a
     * caller cannot act as someone else.
     */
    private Integer signedInUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Integer userId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Please sign in again.");
        }
        return userId;
    }
}
