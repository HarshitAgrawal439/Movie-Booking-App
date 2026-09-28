package com.harshit.backend.bookmyshow;

import com.harshit.backend.bookmyshow.model.Booking;
import com.harshit.backend.bookmyshow.model.BookingSeat;
import com.harshit.backend.bookmyshow.model.BookingStatus;
import com.harshit.backend.bookmyshow.model.Seat;
import com.harshit.backend.bookmyshow.model.SeatStatus;
import com.harshit.backend.bookmyshow.repository.BookingRepository;
import com.harshit.backend.bookmyshow.repository.BookingSeatRepository;
import com.harshit.backend.bookmyshow.repository.SeatRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * Puts the database back into the exact state {@code data.sql} describes before each test.
 *
 * <p>Spring caches one application context per unique configuration, and the test H2 URL uses
 * {@code DB_CLOSE_DELAY=-1}, so the database survives for the whole JVM. Without this reset a
 * booking made by one test method is still there for the next one, and the suite fails in an
 * order-dependent way that looks like a product bug.
 *
 * <p>The alternative — {@code @Transactional} rollback on every test — is not usable for the
 * concurrency tests, which must see committed data from other threads. An explicit reset works
 * for both kinds of test, so the whole suite shares one mechanism.
 */
abstract class AbstractBookingTest {

    /** Highest id written by data.sql; anything above it was created by a test. */
    private static final long SEED_MAX_BOOKING_ID = 2L;

    private static final long SEED_CONFIRMED_BOOKING = 1L;
    private static final long SEED_HELD_BOOKING = 2L;
    private static final long SEED_BOOKED_SEAT_A = 1L;
    private static final long SEED_BOOKED_SEAT_B = 2L;
    private static final long SEED_HELD_SEAT = 17L;

    @Autowired
    protected SeatRepository seatRepository;

    @Autowired
    protected BookingRepository bookingRepository;

    @Autowired
    protected BookingSeatRepository bookingSeatRepository;

    @Autowired
    protected Clock clock;

    @Value("${booking.hold-minutes:5}")
    private long holdMinutes;

    @BeforeEach
    void resetDatabaseToSeedState() {
        LocalDateTime now = LocalDateTime.now(clock);

        // 0. Wipe the seat history, then restore the two seeded rows. Done first because
        //    booking_seat references both bookings and seats.
        bookingSeatRepository.deleteAllInBatch();
        bookingSeatRepository.saveAllAndFlush(List.of(
                BookingSeat.builder().booking(bookingsByName("BMSSEED01")).seat(seatById(SEED_BOOKED_SEAT_A))
                        .seatNumber("A1").price(new java.math.BigDecimal("300.00")).build(),
                BookingSeat.builder().booking(bookingsByName("BMSSEED01")).seat(seatById(SEED_BOOKED_SEAT_B))
                        .seatNumber("A2").price(new java.math.BigDecimal("300.00")).build(),
                BookingSeat.builder().booking(bookingsByName("BMSSEED02")).seat(seatById(SEED_HELD_SEAT))
                        .seatNumber("A1").price(new java.math.BigDecimal("250.00")).build()));

        // 1. Detach every seat from its booking first, otherwise the FK blocks the delete below.
        List<Seat> seats = seatRepository.findAll();
        seats.forEach(s -> {
            s.setBooking(null);
            s.setHoldExpiresAt(null);
            s.setStatus(SeatStatus.AVAILABLE);
        });
        seatRepository.saveAllAndFlush(seats);

        // 2. Remove bookings the tests created, keeping the two seeded ones.
        List<Long> testBookingIds = bookingRepository.findAll().stream()
                .map(Booking::getId)
                .filter(id -> id > SEED_MAX_BOOKING_ID)
                .toList();
        if (!testBookingIds.isEmpty()) {
            bookingRepository.deleteAllByIdInBatch(testBookingIds);
        }

        // 3. Re-apply the seeded holdings.
        Booking confirmed = bookingRepository.findById(SEED_CONFIRMED_BOOKING).orElseThrow();
        Booking held = bookingRepository.findById(SEED_HELD_BOOKING).orElseThrow();

        confirmed.setStatus(BookingStatus.CONFIRMED);
        confirmed.setUpdatedOn(now);
        held.setStatus(BookingStatus.PENDING);
        // Fresh updatedOn keeps the expiry sweeper from expiring the seeded pending booking
        // during the test run, which would make seat 17 unexpectedly free.
        held.setUpdatedOn(now);
        bookingRepository.saveAllAndFlush(List.of(confirmed, held));

        Seat seatA = seatRepository.findById(SEED_BOOKED_SEAT_A).orElseThrow();
        seatA.setStatus(SeatStatus.BOOKED);
        seatA.setBooking(confirmed);

        Seat seatB = seatRepository.findById(SEED_BOOKED_SEAT_B).orElseThrow();
        seatB.setStatus(SeatStatus.BOOKED);
        seatB.setBooking(confirmed);

        Seat heldSeat = seatRepository.findById(SEED_HELD_SEAT).orElseThrow();
        heldSeat.setStatus(SeatStatus.HELD);
        heldSeat.setBooking(held);
        heldSeat.setHoldExpiresAt(now.plusMinutes(holdMinutes));

        seatRepository.saveAllAndFlush(List.of(seatA, seatB, heldSeat));
    }

    /** First AVAILABLE seat of a show, or a failure naming what was actually there. */
    protected Long anAvailableSeatIn(Long showId) {
        return seatRepository.findByShowIdOrderBySeatNo(showId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE)
                .map(Seat::getId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Show " + showId + " has no AVAILABLE seat left"));
    }

    private Seat seatById(Long id) {
        return seatRepository.findById(id).orElseThrow();
    }

    private Booking bookingsByName(String bookingNumber) {
        return bookingRepository.findAll().stream()
                .filter(b -> bookingNumber.equals(b.getBookingNumber()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Seeded booking " + bookingNumber + " is missing"));
    }
}
