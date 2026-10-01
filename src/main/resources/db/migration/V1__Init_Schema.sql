-- V1__Init_Schema.sql
-- Initial database schema for Book-My-Event

CREATE TABLE shows (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    price_paise BIGINT NOT NULL,
    per_user_limit INT NOT NULL DEFAULT 4,
    created_at TIMESTAMP NOT NULL,
    UNIQUE(name)
);

CREATE TABLE seats (
    id UUID PRIMARY KEY,
    show_id UUID NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
    seat_number VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE',
    held_by VARCHAR(255),
    held_until TIMESTAMP,
    confirmed_by VARCHAR(255),
    confirmed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    UNIQUE(show_id, seat_number),
    INDEX idx_show_seat (show_id, seat_number),
    INDEX idx_show_status (show_id, status),
    INDEX idx_held_until (held_until)
);

CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    show_id UUID NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
    user_id VARCHAR(255) NOT NULL,
    amount_paise BIGINT NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'CONFIRMED',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE(user_id, idempotency_key),
    INDEX idx_user_show (user_id, show_id),
    INDEX idx_idempotency (user_id, idempotency_key)
);

CREATE TABLE reservation_seats (
    reservation_id UUID NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    seat_id UUID NOT NULL REFERENCES seats(id),
    PRIMARY KEY (reservation_id, seat_id)
);

-- Create indexes for performance
CREATE INDEX idx_reservations_status ON reservations(status);
CREATE INDEX idx_seats_status_show ON seats(status, show_id);
