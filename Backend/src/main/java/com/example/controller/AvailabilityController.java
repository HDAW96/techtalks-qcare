package com.example.controller;

import com.example.dto.DayAvailabilityResponse;
import com.example.service.BookingService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Free times for one doctor and one treatment.
 *
 * Public, like the doctor and treatment lists, because the booking screen
 * shows times before anyone signs in. It exposes only whether a time is free,
 * never who holds the others.
 *
 * One part needs sign-in: `ignoring`, used when a patient moves an
 * appointment, is accepted only from that appointment's owner (401 when
 * signed out, 404 for anyone else's). Otherwise anyone could pass another
 * patient's appointment id and see which time it frees up.
 */
@RestController
@RequestMapping("/api/doctors")
public class AvailabilityController {

    /** Enough days for the booking screen's day strip. */
    private static final int DEFAULT_DAYS = 5;
    private static final int MAX_DAYS = 30;

    private final BookingService bookingService;

    public AvailabilityController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/{doctorId}/availability")
    public List<DayAvailabilityResponse> availability(
            @PathVariable Integer doctorId,
            @RequestParam Integer serviceId,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Integer ignoring,
            Authentication authentication) {
        int wanted = days == null ? DEFAULT_DAYS : Math.min(Math.max(days, 1), MAX_DAYS);
        Integer userId = authentication != null && authentication.getPrincipal() instanceof Integer id
                ? id : null;
        return bookingService.availability(doctorId, serviceId, wanted, userId, ignoring);
    }
}
