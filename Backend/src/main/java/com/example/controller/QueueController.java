package com.example.controller;

import com.example.dto.DoctorQueueResponse;
import com.example.dto.JoinOptionResponse;
import com.example.dto.JoinQueueDTO;
import com.example.dto.PatientTicketResponse;
import com.example.dto.PublicBoardResponse;
import com.example.dto.OpenQueueDTO;
import com.example.dto.QueueTicketResponse;
import com.example.dto.QueueTicketsResponse;
import com.example.dto.QueueTodayResponse;
import com.example.dto.TicketStatusDTO;
import com.example.dto.WalkInDTO;
import com.example.service.QueueService;
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

/**
 * The front desk's queue.
 *
 * The desk endpoints carry patients' names and phone numbers, so they are
 * receptionist-only - see SecurityConfig. Three are public on purpose and carry
 * no names at all: the join options and joining behind the clinic QR, and the
 * waiting-room board.
 */
@RestController
@RequestMapping("/api/queues")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    /** Today's doctors, the clinic QR, and which queues are open. */
    @GetMapping("/today")
    public QueueTodayResponse today() {
        return queueService.today();
    }

    @PostMapping
    public ResponseEntity<DoctorQueueResponse> open(Authentication authentication,
            @RequestBody OpenQueueDTO request) {
        DoctorQueueResponse opened = queueService.openQueue(
                signedInUserId(authentication), request.getDoctorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(opened);
    }

    /** Nobody new can join; those already in it can still be seen to. */
    @PostMapping("/{queueId}/close")
    public DoctorQueueResponse close(@PathVariable Integer queueId) {
        return queueService.closeQueue(queueId);
    }

    /* ── Public: behind the clinic QR, and the waiting-room screen ── */

    /** The doctors a patient can choose after scanning today's QR. Public. */
    @GetMapping("/join-options")
    public List<JoinOptionResponse> joinOptions(@RequestParam(required = false) String date,
            @RequestParam(required = false) String sig) {
        return queueService.joinOptions(date, sig);
    }

    /**
     * Joins the chosen doctor's queue. Public, but a token still counts: a
     * signed-in patient joins as themselves, anyone else gives a name and
     * phone number.
     */
    @PostMapping("/join")
    public ResponseEntity<PatientTicketResponse> join(Authentication authentication,
            @RequestBody JoinQueueDTO request) {
        Integer userId = authentication != null && authentication.getPrincipal() instanceof Integer id
                ? id : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(queueService.join(userId, request));
    }

    /** Every open queue today, ticket numbers only. Public. */
    @GetMapping("/today/board")
    public List<PublicBoardResponse> board() {
        return queueService.publicBoard();
    }

    @GetMapping("/{queueId}/tickets")
    public QueueTicketsResponse tickets(@PathVariable Integer queueId) {
        return queueService.tickets(queueId);
    }

    @PostMapping("/{queueId}/tickets")
    public ResponseEntity<QueueTicketResponse> addWalkIn(@PathVariable Integer queueId,
            @RequestBody WalkInDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(queueService.addWalkIn(queueId, request));
    }

    /**
     * Call in, skip, finish or bring back. Returns the whole board rather than
     * the one ticket, because moving one person changes the waiting time of
     * everyone behind them.
     */
    @PatchMapping("/{queueId}/tickets/{ticketId}")
    public QueueTicketsResponse setStatus(@PathVariable Integer queueId,
            @PathVariable Integer ticketId,
            @RequestBody TicketStatusDTO request) {
        return queueService.changeStatus(queueId, ticketId, request.getStatus());
    }

    /**
     * The user id comes from the verified token, never from the request body,
     * so a caller cannot open a queue in someone else's name.
     */
    private Integer signedInUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Integer userId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Please sign in again.");
        }
        return userId;
    }
}
