package org.paytmevent.app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationDTO {
    @JsonProperty("reservation_id")
    private UUID reservationId;

    @JsonProperty("show_id")
    private UUID showId;

    @JsonProperty("user_id")
    private String userId;

    private List<String> seats;

    @JsonProperty("amount_paise")
    private long amountPaise;

    private String status;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}
