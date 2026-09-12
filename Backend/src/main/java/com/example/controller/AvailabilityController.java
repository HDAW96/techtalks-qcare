package com.example.controller;

import com.example.dto.DayAvailabilityResponse;
import com.example.service.BookingService;
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
            @RequestParam(required = false) Integer days) {
        int wanted = days == null ? DEFAULT_DAYS : Math.min(Math.max(days, 1), MAX_DAYS);
        return bookingService.availability(doctorId, serviceId, wanted);
    }
}
