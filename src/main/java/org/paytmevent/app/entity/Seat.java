package org.paytmevent.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "seats", indexes = {
        @Index(name = "idx_show_seat", columnList = "show_id, seat_number", unique = true),
        @Index(name = "idx_show_status", columnList = "show_id, status"),
        @Index(name = "idx_held_until", columnList = "held_until")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @Column(nullable = false)
    private String seatNumber;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SeatStatus status;

    @Column
    private String heldBy;

    @Column
    private LocalDateTime heldUntil;

    @Column
    private String confirmedBy;

    @Column
    private LocalDateTime confirmedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.status = SeatStatus.AVAILABLE;
    }
}
