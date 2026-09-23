package com.example.controller;

import com.example.dto.DoctorLeaveDTO;
import com.example.dto.DoctorLeaveResponse;
import com.example.dto.DoctorScheduleDTO;
import com.example.service.DoctorScheduleService;
import com.example.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
     * Blocks a range of days as vacation or sick leave, so the booking
     * screen stops offering any time in it and a booking or reschedule for
     * one of those days is refused (see BookingService).
     */
    @PostMapping("/leave")
    public ResponseEntity<DoctorLeaveResponse> addLeave(Authentication authentication,
            @RequestBody DoctorLeaveDTO request) {
        DoctorLeaveResponse created = scheduleService.addLeave(signedInUserId(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** This doctor's own leave, earliest first. */
    @GetMapping("/leave")
    public List<DoctorLeaveResponse> myLeave(Authentication authentication) {
        return scheduleService.myLeave(signedInUserId(authentication));
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
