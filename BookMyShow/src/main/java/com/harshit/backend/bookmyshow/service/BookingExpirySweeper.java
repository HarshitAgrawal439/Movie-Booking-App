package com.harshit.backend.bookmyshow.service;

import com.harshit.backend.bookmyshow.model.BookingStatus;
import com.harshit.backend.bookmyshow.model.SeatStatus;
import com.harshit.backend.bookmyshow.repository.BookingRepository;
import com.harshit.backend.bookmyshow.repository.SeatRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Returns seats abandoned at the payment page back to the pool.
 *
 * <p>Correctness does not depend on this job running. Availability is decided by comparing
 * {@code hold_expires_at} against the current time inside the claim UPDATE, so an expired hold is
 * already reclaimable the moment it expires. This sweeper only exists so the stored status stops
 * saying HELD and the booking row reaches a terminal state.
 *
 * <p>At scale you would not poll a database for this. A Redis key per hold with a TTL, or a
 * delayed message queue, releases the seat without a periodic scan.
 */
@Component
public class BookingExpirySweeper {

    private static final Logger log = LoggerFactory.getLogger(BookingExpirySweeper.class);

    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final Clock clock;
    private final long holdMinutes;

    public BookingExpirySweeper(
            SeatRepository seatRepository,
            BookingRepository bookingRepository,
            Clock clock,
            @org.springframework.beans.factory.annotation.Value("${booking.hold-minutes:5}") long holdMinutes) {
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
        this.clock = clock;
        this.holdMinutes = holdMinutes;
    }

    @Scheduled(fixedDelayString = "${booking.sweep-interval-ms:30000}")
    @Transactional
    public void releaseExpiredHolds() {
        LocalDateTime now = LocalDateTime.now(clock);

        int seats = seatRepository.releaseExpiredHolds(SeatStatus.HELD, SeatStatus.AVAILABLE, now);
        int bookings = bookingRepository.expireStaleBookings(
                BookingStatus.PENDING, BookingStatus.EXPIRED, now, now.minusMinutes(holdMinutes));

        if (seats > 0 || bookings > 0) {
            log.info("Sweeper released {} seats and expired {} bookings", seats, bookings);
        }
    }
}
