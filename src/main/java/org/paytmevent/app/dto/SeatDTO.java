package org.paytmevent.app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatDTO {
    private String id;

    @JsonProperty("seat_number")
    private String seatNumber;

    private String status;

    @JsonProperty("held_by")
    private String heldBy;

    @JsonProperty("confirmed_by")
    private String confirmedBy;
}
