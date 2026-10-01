package org.paytmevent.app.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ReservationMetrics {

    private final Counter confirmedReservations;
    private final Counter declinedSeatTaken;
    private final Counter declinedPerUserLimit;
    private final Counter declinedIdempotent;

    public ReservationMetrics(MeterRegistry registry) {
        this.confirmedReservations = Counter.builder("reservations.confirmed")
                .description("Total seats confirmed")
                .register(registry);

        this.declinedSeatTaken = Counter.builder("reservations.declined.seat_taken")
                .description("Declined because seat already taken")
                .register(registry);

        this.declinedPerUserLimit = Counter.builder("reservations.declined.per_user_limit")
                .description("Declined because user exceeded limit")
                .register(registry);

        this.declinedIdempotent = Counter.builder("reservations.declined.idempotent_replay")
                .description("Idempotent request replays")
                .register(registry);
    }

    public void recordConfirmed(int seatCount) {
        confirmedReservations.increment(seatCount);
    }

    public void recordDecline(String reason) {
        switch (reason) {
            case "seat_taken" -> declinedSeatTaken.increment();
            case "per_user_limit" -> declinedPerUserLimit.increment();
            case "idempotent_replay" -> declinedIdempotent.increment();
            default -> {}
        }
    }

    public void recordIdempotentReplay() {
        declinedIdempotent.increment();
    }
}
