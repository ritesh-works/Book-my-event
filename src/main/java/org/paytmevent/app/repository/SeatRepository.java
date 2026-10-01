package org.paytmevent.app.repository;

import org.paytmevent.app.entity.Seat;
import org.paytmevent.app.entity.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeatRepository extends JpaRepository<Seat, UUID> {

    @Query("SELECT s FROM Seat s WHERE s.show.id = :showId AND s.seatNumber = :seatNumber")
    Optional<Seat> findByShowIdAndSeatNumber(
            @Param("showId") UUID showId,
            @Param("seatNumber") String seatNumber
    );

    @Query("SELECT s FROM Seat s WHERE s.show.id = :showId ORDER BY s.seatNumber")
    List<Seat> findAllByShowIdOrderedBySeatNumber(@Param("showId") UUID showId);

    @Modifying
    @Query("UPDATE Seat s SET s.status = 'CONFIRMED', s.confirmedBy = :userId, s.confirmedAt = CURRENT_TIMESTAMP " +
            "WHERE s.show.id = :showId AND s.seatNumber = :seatNumber AND s.status = 'AVAILABLE'")
    int atomicConfirmSeat(
            @Param("showId") UUID showId,
            @Param("seatNumber") String seatNumber,
            @Param("userId") String userId
    );

    @Modifying
    @Query("UPDATE Seat s SET s.status = 'HELD', s.heldBy = :userId, s.heldUntil = :expiryTime " +
            "WHERE s.show.id = :showId AND s.seatNumber = :seatNumber AND s.status = 'AVAILABLE'")
    int atomicHoldSeat(
            @Param("showId") UUID showId,
            @Param("seatNumber") String seatNumber,
            @Param("userId") String userId,
            @Param("expiryTime") LocalDateTime expiryTime
    );

    @Modifying
    @Query("UPDATE Seat s SET s.status = 'AVAILABLE', s.heldBy = NULL, s.heldUntil = NULL " +
            "WHERE s.show.id = :showId AND s.status = 'HELD' AND s.heldUntil < CURRENT_TIMESTAMP")
    void expireHeldSeats(@Param("showId") UUID showId);

    @Query("SELECT COUNT(s) FROM Seat s WHERE s.show.id = :showId AND s.status = 'AVAILABLE'")
    long countAvailableSeats(@Param("showId") UUID showId);

    @Query("SELECT COUNT(s) FROM Seat s WHERE s.show.id = :showId AND s.status = 'HELD'")
    long countHeldSeats(@Param("showId") UUID showId);

    @Query("SELECT COUNT(s) FROM Seat s WHERE s.show.id = :showId AND s.status = 'CONFIRMED'")
    long countConfirmedSeats(@Param("showId") UUID showId);
}
