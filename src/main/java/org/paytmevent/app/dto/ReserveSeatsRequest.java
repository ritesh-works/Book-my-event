package org.paytmevent.app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReserveSeatsRequest {
    private List<String> seats;

    @JsonProperty("idempotency_key")
    private String idempotencyKey;
}
