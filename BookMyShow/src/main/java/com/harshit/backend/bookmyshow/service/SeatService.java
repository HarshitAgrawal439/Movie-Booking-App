package com.harshit.backend.bookmyshow.service;

import com.harshit.backend.bookmyshow.dto.SeatResponse;
import com.harshit.backend.bookmyshow.exception.ResourceNotFoundException;
import com.harshit.backend.bookmyshow.model.Seat;
import com.harshit.backend.bookmyshow.model.SeatStatus;
import com.harshit.backend.bookmyshow.repository.SeatRepository;
import com.harshit.backend.bookmyshow.repository.ShowRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeatService {

    private final SeatRepository seatRepository;
    private final ShowRepository showRepository;

    public SeatService(SeatRepository seatRepository, ShowRepository showRepository) {
        this.seatRepository = seatRepository;
        this.showRepository = showRepository;
    }

    /**
     * Returns every seat of the show, including unavailable ones.
     *
     * <p>Returning taken seats is deliberate: the client needs them greyed out so the user sees
     * the hall layout. A seat whose hold has lapsed is reported as AVAILABLE even though no
     * sweeper has run yet, so the display never shows a seat as blocked past its deadline.
     */
    @Transactional(readOnly = true)
    public List<SeatResponse> getSeatsByShowId(Long showId) {
        if (!showRepository.existsById(showId)) {
            throw new ResourceNotFoundException("Show " + showId + " not found");
        }
        LocalDateTime now = LocalDateTime.now();
        return seatRepository.findByShowIdOrderBySeatNo(showId).stream()
                .map(seat -> SeatResponse.from(normaliseLapsedHold(seat, now)))
                .toList();
    }

    private Seat normaliseLapsedHold(Seat seat, LocalDateTime now) {
        if (seat.isAvailableAt(now) && seat.getStatus() == SeatStatus.HELD) {
            Seat copy = new Seat();
            copy.setId(seat.getId());
            copy.setSeatNo(seat.getSeatNo());
            copy.setPrice(seat.getPrice());
            copy.setShow(seat.getShow());
            copy.setStatus(SeatStatus.AVAILABLE);
            copy.setHoldExpiresAt(null);
            return copy;
        }
        return seat;
    }
}
