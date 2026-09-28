package com.harshit.backend.bookmyshow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.harshit.backend.bookmyshow.dto.BookingResponse;
import com.harshit.backend.bookmyshow.dto.ReserveSeatsRequest;
import com.harshit.backend.bookmyshow.exception.InvalidBookingStateException;
import com.harshit.backend.bookmyshow.exception.SeatsUnavailableException;
import com.harshit.backend.bookmyshow.model.BookingStatus;
import com.harshit.backend.bookmyshow.model.SeatStatus;
import com.harshit.backend.bookmyshow.service.BookingExpirySweeper;
import com.harshit.backend.bookmyshow.service.BookingService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Hold expiry, driven by a movable clock rather than {@code Thread.sleep}.
 *
 * <p>Overriding the {@code Clock} bean is the reason the production code takes a
 * {@code Clock} instead of calling {@code LocalDateTime.now()}: a time-dependent rule that can
 * only be tested by sleeping is a rule that is never tested.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "booking.hold-minutes=5")
@Import(BookingHoldExpiryTest.MovableClockConfig.class)
class BookingHoldExpiryTest extends AbstractBookingTest {

    @TestConfiguration
    static class MovableClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            // Starts at the real current instant so the seeded shows are in the future;
            // tests then move this forward explicitly.
            return new MutableClock(Instant.now());
        }
    }

    /** Clock whose instant can be moved forward by tests. */
    static class MutableClock extends Clock {
        private final Instant baseline;
        private Instant now;

        MutableClock(Instant start) {
            this.baseline = start;
            this.now = start;
        }

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        void rewindToBaseline() {
            now = baseline;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingExpirySweeper sweeper;

    @Autowired
    private MutableClock clock;

    /** Show 4 plays tomorrow, so it stays bookable however far the clock is moved. */
    private static final Long SHOW_ID = 4L;

    private Long seatId;

    @BeforeEach
    void pickAvailableSeatAndRewindClock() {
        // The clock is shared across the class, so rewind it before each test or the second
        // test in the class starts with a hold that has already expired.
        clock.rewindToBaseline();
        seatId = anAvailableSeatIn(SHOW_ID);
    }

    @Test
    @DisplayName("a held seat cannot be taken by another user before the hold expires")
    void holdBlocksOtherUsers() {
        bookingService.reserveSeats(new ReserveSeatsRequest(SHOW_ID, List.of(seatId), "holder@example.com"));

        assertThatThrownBy(() -> bookingService.reserveSeats(new ReserveSeatsRequest(SHOW_ID, List.of(seatId), "thief@example.com")))
                .isInstanceOf(SeatsUnavailableException.class);

        clock.advance(Duration.ofMinutes(4).plusSeconds(59));
        assertThatThrownBy(() -> bookingService.reserveSeats(new ReserveSeatsRequest(SHOW_ID, List.of(seatId), "thief2@example.com")))
                .isInstanceOf(SeatsUnavailableException.class);
    }

    @Test
    @DisplayName("once the hold lapses the seat becomes claimable again, without any sweeper")
    void lapsedHoldIsReclaimableWithoutSweeper() {
        bookingService.reserveSeats(new ReserveSeatsRequest(SHOW_ID, List.of(seatId), "holder@example.com"));

        clock.advance(Duration.ofMinutes(6));

        // No sweeper call here on purpose: availability is decided by comparing hold_expires_at
        // to now inside the claim statement, so correctness does not depend on a background job.
        BookingResponse second = bookingService.reserveSeats(new ReserveSeatsRequest(SHOW_ID, List.of(seatId), "taker@example.com"));

        assertThat(second.status()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    @DisplayName("confirming after the hold lapsed is refused rather than silently re-reserving")
    void confirmAfterExpiryIsRefused() {
        BookingResponse booking = bookingService.reserveSeats(new ReserveSeatsRequest(SHOW_ID, List.of(seatId), "slow@example.com"));

        clock.advance(Duration.ofMinutes(6));
        sweeper.releaseExpiredHolds();

        assertThatThrownBy(() -> bookingService.confirmBooking(booking.id()))
                .isInstanceOf(InvalidBookingStateException.class);
    }

    @Test
    @DisplayName("the sweeper moves lapsed holds back to AVAILABLE and expires the booking")
    void sweeperReleasesLapsedHolds() {
        BookingResponse booking = bookingService.reserveSeats(new ReserveSeatsRequest(SHOW_ID, List.of(seatId), "sweeper@example.com"));

        assertThat(seatRepository.findByShowIdAndIdIn(SHOW_ID, List.of(seatId)).get(0).getStatus())
                .isEqualTo(SeatStatus.HELD);

        clock.advance(Duration.ofMinutes(6));
        sweeper.releaseExpiredHolds();

        assertThat(seatRepository.findByShowIdAndIdIn(SHOW_ID, List.of(seatId)).get(0).getStatus())
                .isEqualTo(SeatStatus.AVAILABLE);
        assertThat(bookingService.getBooking(booking.id()).status())
                .isEqualTo(BookingStatus.EXPIRED);
    }

    @Test
    @DisplayName("a confirmed booking is never swept away by the expiry job")
    void confirmedBookingsSurviveTheSweeper() {
        BookingResponse booking = bookingService.reserveSeats(new ReserveSeatsRequest(SHOW_ID, List.of(seatId), "payer@example.com"));
        bookingService.confirmBooking(booking.id());

        clock.advance(Duration.ofHours(3));
        sweeper.releaseExpiredHolds();

        assertThat(seatRepository.findByShowIdAndIdIn(SHOW_ID, List.of(seatId)).get(0).getStatus())
                .isEqualTo(SeatStatus.BOOKED);
        assertThat(bookingService.getBooking(booking.id()).status())
                .isEqualTo(BookingStatus.CONFIRMED);
    }
}
