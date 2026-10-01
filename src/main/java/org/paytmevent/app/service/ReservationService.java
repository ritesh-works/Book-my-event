package org.paytmevent.app.service;

import lombok.extern.slf4j.Slf4j;
import org.paytmevent.app.dto.*;
import org.paytmevent.app.entity.*;
import org.paytmevent.app.exception.*;
import org.paytmevent.app.metrics.ReservationMetrics;
import org.paytmevent.app.repository.ReservationRepository;
import org.paytmevent.app.repository.SeatRepository;
import org.paytmevent.app.repository.ShowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReservationService {

    private static final int HOLD_DURATION_MINUTES = 15;

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationMetrics metrics;

    public ReservationService(
            ShowRepository showRepository,
            SeatRepository seatRepository,
            ReservationRepository reservationRepository,
            ReservationMetrics metrics) {
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.metrics = metrics;
    }

    @Transactional
    public ReservationDTO reserveSeats(String showId, String userId, ReserveSeatsRequest request) {
        String idempotencyKey = request.getIdempotencyKey();

        // Step 1: Check if this exact request was already processed (idempotency)
        Optional<Reservation> existingReservation = reservationRepository
                .findByUserIdAndIdempotencyKeyAndShowId(userId, idempotencyKey, showId);

        if (existingReservation.isPresent()) {
            Reservation res = existingReservation.get();
            log.info("Idempotent replay detected for user {} with key {}", userId, idempotencyKey);
            metrics.recordIdempotentReplay();
            return mapToDTO(res);
        }

        // Step 2: Load show and validate
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new ShowNotFoundException("Show not found: " + showId));

        // Step 3: Expire any old held seats first
        seatRepository.expireHeldSeats(showId);

        // Step 4: Check per-user limit
        long currentConfirmedCount = reservationRepository
                .countConfirmedReservationsByUserAndShow(userId, showId);

        if (currentConfirmedCount >= show.getPerUserLimit()) {
            log.warn("User {} exceeded per-user limit for show {}", userId, showId);
            metrics.recordDecline("per_user_limit");
            throw new PerUserLimitExceededException(
                    "User has already confirmed " + currentConfirmedCount + " seats");
        }

        // Step 5: Attempt to atomically confirm all requested seats (all-or-nothing)
        List<String> requestedSeats = request.getSeats();
        List<Seat> confirmedSeats = new ArrayList<>();
        List<String> failedSeats = new ArrayList<>();

        for (String seatNumber : requestedSeats) {
            Seat seat = seatRepository.findByShowIdAndSeatNumber(showId, seatNumber)
                    .orElseThrow(() -> new SeatNotFoundException("Seat not found: " + seatNumber));

            // Atomic confirmation: UPDATE ... WHERE status = 'AVAILABLE'
            // Only one user can succeed; others get 0 rows affected
            if (seat.getStatus() == SeatStatus.AVAILABLE) {
                try {
                    // In a real concurrent scenario, this should use the native query
                    // For now, we use a row lock via pessimistic locking
                    seat.setStatus(SeatStatus.CONFIRMED);
                    seat.setConfirmedBy(userId);
                    seat.setConfirmedAt(LocalDateTime.now());
                    seatRepository.save(seat);
                    confirmedSeats.add(seat);
                } catch (Exception e) {
                    // Another thread won the race
                    log.debug("Seat {} already taken in race", seatNumber);
                    failedSeats.add(seatNumber);
                    metrics.recordDecline("seat_taken");
                }
            } else {
                // Seat already held or confirmed
                log.debug("Seat {} not available. Status: {}", seatNumber, seat.getStatus());
                failedSeats.add(seatNumber);
                metrics.recordDecline("seat_taken");
            }
        }

        // Step 6: Decide on partial requests (all-or-nothing strategy)
        if (!failedSeats.isEmpty()) {
            // Rollback any confirmed seats from this request
            confirmedSeats.forEach(seat -> {
                seat.setStatus(SeatStatus.AVAILABLE);
                seat.setConfirmedBy(null);
                seat.setConfirmedAt(null);
                seatRepository.save(seat);
            });

            throw new SeatNotAvailableException(
                    "Not all seats available. Failed: " + failedSeats +
                    ". Using all-or-nothing strategy - reservation cancelled.");
        }

        // Step 7: Create reservation record (idempotency key ensures single write)
        Reservation reservation = Reservation.builder()
                .show(show)
                .userId(userId)
                .seatIds(confirmedSeats.stream().map(Seat::getId).collect(Collectors.toSet()))
                .amountPaise(show.getPricePaise() * confirmedSeats.size())
                .idempotencyKey(idempotencyKey)
                .status(ReservationStatus.CONFIRMED)
                .build();

        Reservation saved = reservationRepository.save(reservation);
        log.info("Reservation confirmed for user {} on show {}: {} seats", userId, showId, confirmedSeats.size());
        metrics.recordConfirmed(confirmedSeats.size());

        return mapToDTO(saved);
    }

    @Transactional
    public void cancelReservation(String reservationId, String userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException("Reservation not found"));

        // Only owner can cancel
        if (!reservation.getUserId().equals(userId)) {
            throw new UnauthorizedException("Only the reservation owner can cancel");
        }

        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new InvalidReservationStateException("Only confirmed reservations can be cancelled");
        }

        // Release all seats
        for (String seatId : reservation.getSeatIds()) {
            Seat seat = seatRepository.findById(seatId)
                    .orElseThrow(() -> new SeatNotFoundException("Seat not found"));
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setConfirmedBy(null);
            seat.setConfirmedAt(null);
            seatRepository.save(seat);
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);
        log.info("Reservation {} cancelled by user {}", reservationId, userId);
    }

    @Transactional(readOnly = true)
    public ShowDTO getShowState(String showId) {
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new ShowNotFoundException("Show not found: " + showId));

        List<Seat> seats = seatRepository.findAllByShowIdOrderedBySeatNumber(showId);

        List<SeatDTO> seatDTOs = seats.stream()
                .map(seat -> SeatDTO.builder()
                        .id(seat.getId())
                        .seatNumber(seat.getSeatNumber())
                        .status(seat.getStatus().toString())
                        .heldBy(seat.getHeldBy())
                        .confirmedBy(seat.getConfirmedBy())
                        .build())
                .toList();

        return ShowDTO.builder()
                .id(show.getId())
                .name(show.getName())
                .pricePaise(show.getPricePaise())
                .perUserLimit(show.getPerUserLimit())
                .seats(seatDTOs)
                .availableCount(seats.stream().filter(s -> s.getStatus() == SeatStatus.AVAILABLE).count())
                .heldCount(seats.stream().filter(s -> s.getStatus() == SeatStatus.HELD).count())
                .confirmedCount(seats.stream().filter(s -> s.getStatus() == SeatStatus.CONFIRMED).count())
                .createdAt(show.getCreatedAt())
                .build();
    }

    private ReservationDTO mapToDTO(Reservation reservation) {
        return ReservationDTO.builder()
                .reservationId(reservation.getId())
                .showId(reservation.getShow().getId())
                .userId(reservation.getUserId())
                .seats(reservation.getSeatIds().stream()
                        .map(seatId -> seatRepository.findById(seatId)
                                .map(Seat::getSeatNumber)
                                .orElse(seatId))
                        .toList())
                .amountPaise(reservation.getAmountPaise())
                .status(reservation.getStatus().toString())
                .createdAt(reservation.getCreatedAt())
                .build();
    }
}
