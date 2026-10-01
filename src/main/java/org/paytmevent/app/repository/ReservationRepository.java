package org.paytmevent.app.repository;

import org.paytmevent.app.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, String> {

    @Query("SELECT r FROM Reservation r WHERE r.userId = :userId AND r.idempotencyKey = :key AND r.show.id = :showId")
    Optional<Reservation> findByUserIdAndIdempotencyKeyAndShowId(
            @Param("userId") String userId,
            @Param("key") String idempotencyKey,
            @Param("showId") String showId
    );

    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.userId = :userId AND r.show.id = :showId AND r.status = 'CONFIRMED'")
    long countConfirmedReservationsByUserAndShow(
            @Param("userId") String userId,
            @Param("showId") String showId
    );
}
