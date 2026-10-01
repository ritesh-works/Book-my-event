package org.paytmevent.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "shows")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Show {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private long pricePaise;

    @Column(nullable = false)
    private int perUserLimit;

    @OneToMany(mappedBy = "show", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Seat> seats;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.perUserLimit = 4;
    }

    public long getAvailableSeatsCount() {
        return seats.stream().filter(s -> s.getStatus() == SeatStatus.AVAILABLE).count();
    }

    public long getConfirmedSeatsCount() {
        return seats.stream().filter(s -> s.getStatus() == SeatStatus.CONFIRMED).count();
    }

    public long getHeldSeatsCount() {
        return seats.stream().filter(s -> s.getStatus() == SeatStatus.HELD).count();
    }
}
