package com.example.controller;
import com.example.dto.TreatmentCompletionO;
import com.example.dto.AppointmentBookingDTO;
import com.example.dto.AppointmentRescheduleDTO;
import com.example.dto.AppointmentResponse;
import com.example.dto.DeskAppointmentResponse;
import com.example.service.BookingService;
import com.example.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final BookingService bookingService;

    public AppointmentController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /** The signed-in patient's own appointments, never anyone else's. */
    @GetMapping("/me")
    public List<AppointmentResponse> mine(Authentication authentication) {
        return bookingService.forSignedInPatient(signedInUserId(authentication));
    }

    /**
     * Every appointment on one day (today when no date is given), with who
     * each is for. Reception only; anyone else gets 403.
     */
    @GetMapping
    public List<DeskAppointmentResponse> forDay(Authentication authentication,
            @RequestParam(required = false) String date) {
        return bookingService.forDay(signedInUserId(authentication), date);
    }

    @PostMapping
    public ResponseEntity<AppointmentResponse> book(Authentication authentication,
            @RequestBody AppointmentBookingDTO request) {
        AppointmentResponse booked = bookingService.book(signedInUserId(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(booked);
    }

    /**
     * Moves an appointment to a new time: a patient's own, or anyone's when
     * the caller is reception.
     */
    @PatchMapping("/{appointmentId}")
    public AppointmentResponse reschedule(Authentication authentication,
            @PathVariable Integer appointmentId,
            @RequestBody AppointmentRescheduleDTO request) {
        return bookingService.reschedule(signedInUserId(authentication), appointmentId,
                request.getDatetime());
    }

    /**
     * Cancels one of the caller's own appointments. A POST to an action, not
     * a DELETE: the record stays, marked CANCELLED, as part of the patient's
     * history.
     */
    @PostMapping("/{appointmentId}/cancel")
    public AppointmentResponse cancel(Authentication authentication,
            @PathVariable Integer appointmentId) {
        return bookingService.cancel(signedInUserId(authentication), appointmentId);
    }

    /**
     * The user id comes from the verified token, never from the request body,
     * so a caller cannot act as someone else.
     */
    private Integer signedInUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Integer userId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Please sign in again.");
        }
        return userId;
    }

    @PostMapping("/{appointmentId}/complete")
public AppointmentResponse complete(
        Authentication authentication,
        @PathVariable Integer appointmentId,
        @RequestBody TreatmentCompletion dto) {

    return bookingService.complete(
            signedInUserId(authentication),
            appointmentId,
            dto.getClinicalNotes()
    );
}
}
