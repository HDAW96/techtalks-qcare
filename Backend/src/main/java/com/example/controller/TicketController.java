package com.example.controller;

import com.example.dto.PatientTicketResponse;
import com.example.service.QueueService;
import com.example.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A patient following their own place in the queue. Both answers carry only
 * that ticket and who is in the chair - never anyone else's details.
 */
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final QueueService queueService;

    public TicketController(QueueService queueService) {
        this.queueService = queueService;
    }

    /** The signed-in patient's ticket today; 204 when they are in no queue. */
    @GetMapping("/me")
    public ResponseEntity<PatientTicketResponse> mine(Authentication authentication) {
        Integer userId = signedInUserId(authentication);
        return queueService.myTicket(userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Give up a waiting ticket owned by the signed-in patient. */
    @DeleteMapping("/{ticketId}")
    public ResponseEntity<Void> leave(Authentication authentication, @PathVariable Integer ticketId) {
        queueService.leaveQueue(signedInUserId(authentication), ticketId);
        return ResponseEntity.noContent().build();
    }

    /** For patients without an account: the code their browser kept on joining. Public. */
    @GetMapping("/by-code/{accessCode}")
    public PatientTicketResponse byCode(@PathVariable String accessCode) {
        return queueService.ticketByCode(accessCode);
    }

    /** For patients without an account: the access code proves ownership. */
    @DeleteMapping("/by-code/{accessCode}")
    public ResponseEntity<Void> leaveByCode(@PathVariable String accessCode) {
        queueService.leaveQueueByCode(accessCode);
        return ResponseEntity.noContent().build();
    }

    private Integer signedInUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Integer userId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Please sign in again.");
        }
        return userId;
    }
}
