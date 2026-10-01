package org.paytmevent.app.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class HealthController {

    @GetMapping("/health/live")
    public ResponseEntity<HealthResponse> live() {
        return ResponseEntity.ok(new HealthResponse("UP"));
    }

    @GetMapping("/health/ready")
    public ResponseEntity<HealthResponse> ready() {
        return ResponseEntity.ok(new HealthResponse("READY"));
    }

    static class HealthResponse {
        public String status;

        public HealthResponse(String status) {
            this.status = status;
        }
    }
}
