package com.example.controller;

import com.example.dto.DoctorScheduleDTO;
import com.example.service.DoctorScheduleService;
import com.example.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in doctor's own screens.
 *
 * Kept apart from PatientHistoryController (SCRUM-75) so the two could be
 * built side by side without editing the same file.
 */
@RestController
@RequestMapping("/api/doctors/me")
public class DoctorScheduleController {

    private final DoctorScheduleService scheduleService;

    public DoctorScheduleController(DoctorScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    /** Today's appointments for this doctor, plus whoever is in their chair. */
    @GetMapping("/schedule")
    public DoctorScheduleDTO schedule(Authentication authentication) {
        return scheduleService.todaysSchedule(signedInUserId(authentication));
    }

    /**
     * The user id comes from the verified token, never from the request, so a
     * caller cannot ask for someone else's day.
     */
    private Integer signedInUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Integer userId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Please sign in again.");
        }
        return userId;
    }
}
