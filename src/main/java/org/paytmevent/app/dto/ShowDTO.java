package org.paytmevent.app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShowDTO {
    private String id;
    private String name;

    @JsonProperty("price_paise")
    private long pricePaise;

    @JsonProperty("per_user_limit")
    private int perUserLimit;

    private List<SeatDTO> seats;

    @JsonProperty("available_count")
    private long availableCount;

    @JsonProperty("held_count")
    private long heldCount;

    @JsonProperty("confirmed_count")
    private long confirmedCount;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}
