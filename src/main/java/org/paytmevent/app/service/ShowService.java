package org.paytmevent.app.service;

import lombok.extern.slf4j.Slf4j;
import org.paytmevent.app.dto.CreateShowRequest;
import org.paytmevent.app.dto.ShowDTO;
import org.paytmevent.app.entity.Seat;
import org.paytmevent.app.entity.Show;
import org.paytmevent.app.exception.ShowNotFoundException;
import org.paytmevent.app.repository.SeatRepository;
import org.paytmevent.app.repository.ShowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@Slf4j
public class ShowService {

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final ReservationService reservationService;

    public ShowService(ShowRepository showRepository, SeatRepository seatRepository, ReservationService reservationService) {
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
        this.reservationService = reservationService;
    }

    @Transactional
    public ShowDTO createShow(CreateShowRequest request) {
        Show show = Show.builder()
                .name(request.getName())
                .pricePaise(request.getPricePaise())
                .perUserLimit(request.getPerUserLimit() > 0 ? request.getPerUserLimit() : 4)
                .seats(new HashSet<>())
                .build();

        Show savedShow = showRepository.save(show);
        log.info("Show created: {} with {} seats", savedShow.getId(), request.getSeats().size());

        // Create seats
        Set<Seat> seats = new HashSet<>();
        for (String seatNumber : request.getSeats()) {
            Seat seat = Seat.builder()
                    .show(savedShow)
                    .seatNumber(seatNumber)
                    .build();
            seats.add(seatRepository.save(seat));
        }

        savedShow.setSeats(seats);
        return reservationService.getShowState(savedShow.getId());
    }

    @Transactional(readOnly = true)
    public ShowDTO getShow(String showId) {
        showRepository.findById(showId)
                .orElseThrow(() -> new ShowNotFoundException("Show not found: " + showId));
        return reservationService.getShowState(showId);
    }
}
