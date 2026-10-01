package org.paytmevent.app.controller;

import lombok.extern.slf4j.Slf4j;
import org.paytmevent.app.dto.*;
import org.paytmevent.app.exception.*;
import org.paytmevent.app.service.ReservationService;
import org.paytmevent.app.service.ShowService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/shows")
@Slf4j
public class ShowController {

    private final ShowService showService;
    private final ReservationService reservationService;

    public ShowController(ShowService showService, ReservationService reservationService) {
        this.showService = showService;
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ShowDTO> createShow(@RequestBody CreateShowRequest request) {
        log.info("Creating show: {}", request.getName());
        ShowDTO show = showService.createShow(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(show);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShowDTO> getShow(@PathVariable String id) {
        log.debug("Fetching show: {}", id);
        UUID showId = UUID.fromString(id);
        ShowDTO show = showService.getShow(showId);
        return ResponseEntity.ok(show);
    }

    @PostMapping("/{id}/reserve")
    public ResponseEntity<?> reserveSeats(
            @PathVariable String id,
            @RequestBody ReserveSeatsRequest request,
            @RequestHeader(value = "X-User-Id", required = true) String userId) {

        try {
            log.info("Reserve request from user {} for show {}, seats: {}", 
                    userId, id, request.getSeats());

            UUID showId = UUID.fromString(id);
            ReservationDTO reservation = reservationService.reserveSeats(showId, userId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(reservation);

        } catch (PerUserLimitExceededException e) {
            log.warn("Per-user limit exceeded: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("per_user_limit_exceeded", e.getMessage()));

        } catch (SeatNotAvailableException e) {
            log.warn("Seats not available: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("seats_not_available", e.getMessage()));

        } catch (ShowNotFoundException | SeatNotFoundException e) {
            log.error("Resource not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("not_found", e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error during reservation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("internal_error", e.getMessage()));
        }
    }

    @PostMapping("/reservations/{id}/cancel")
    public ResponseEntity<?> cancelReservation(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Id", required = true) String userId) {

        try {
            log.info("Cancel request for reservation {} from user {}", id, userId);
            UUID reservationId = UUID.fromString(id);
            reservationService.cancelReservation(reservationId, userId);
            return ResponseEntity.noContent().build();

        } catch (UnauthorizedException e) {
            log.warn("Unauthorized cancel: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("unauthorized", e.getMessage()));

        } catch (ReservationNotFoundException e) {
            log.warn("Reservation not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("not_found", e.getMessage()));

        } catch (InvalidReservationStateException e) {
            log.warn("Invalid reservation state: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("invalid_state", e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error during cancellation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("internal_error", e.getMessage()));
        }
    }
}
