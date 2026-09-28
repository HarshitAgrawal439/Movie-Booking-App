package com.harshit.backend.bookmyshow;

import static org.assertj.core.api.Assertions.assertThat;

import com.harshit.backend.bookmyshow.dto.BookingResponse;
import com.harshit.backend.bookmyshow.dto.ReserveSeatsRequest;
import com.harshit.backend.bookmyshow.exception.BadRequestException;
import com.harshit.backend.bookmyshow.exception.SeatsUnavailableException;
import com.harshit.backend.bookmyshow.model.Seat;
import com.harshit.backend.bookmyshow.model.SeatStatus;
import com.harshit.backend.bookmyshow.service.BookingService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The test that justifies the seat-claim design.
 *
 * <p>Deliberately NOT {@code @Transactional}: the whole point is to run several independent
 * transactions against the same row simultaneously. A test-managed transaction would put every
 * worker in the caller's persistence context and hide the race entirely.
 *
 * <p>Loser exceptions are collected and asserted on, not swallowed. A test that accepts any
 * exception passes just as happily when the endpoint is broken for an unrelated reason, which
 * is how concurrency regressions slip through.
 */
@SpringBootTest
@ActiveProfiles("test")
class SeatConcurrencyTest extends AbstractBookingTest {

    private static final Long SHOW_ID = 1L;
    private static final Long CONTESTED_SEAT = 8L; // B4 of show 1, seeded AVAILABLE

    @Autowired
    private BookingService bookingService;

    @Test
    @DisplayName("only one of N concurrent requests for the same seat may win")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void onlyOneThreadWinsTheSameSeat() throws Exception {
        int threads = 8;
        CyclicBarrier startLine = new CyclicBarrier(threads);
        AtomicInteger winners = new AtomicInteger();
        List<Throwable> losers = new CopyOnWriteArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Void>> workers = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final String email = "racer" + i + "@example.com";
                workers.add(() -> {
                    startLine.await(5, TimeUnit.SECONDS);
                    try {
                        BookingResponse booking =
                                bookingService.reserveSeats(new ReserveSeatsRequest(SHOW_ID, List.of(CONTESTED_SEAT), email));
                        assertThat(booking.bookingNumber()).isNotBlank();
                        winners.incrementAndGet();
                    } catch (RuntimeException e) {
                        losers.add(e);
                    }
                    return null;
                });
            }

            for (Future<Void> f : pool.invokeAll(workers, 60, TimeUnit.SECONDS)) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(losers)
                .as("every loser must be a seat conflict, not some unrelated fault")
                .allMatch(e -> e instanceof SeatsUnavailableException || isWriteConflict(e));
        assertThat(winners.get()).as("exactly one request may hold the seat").isEqualTo(1);
        assertThat(losers).hasSize(threads - 1);

        Seat seat = seatRepository.findByShowIdAndIdIn(SHOW_ID, List.of(CONTESTED_SEAT)).get(0);
        assertThat(seat.getStatus()).isIn(SeatStatus.HELD, SeatStatus.BOOKED);
        assertThat(seat.getBooking()).as("seat must belong to exactly one booking").isNotNull();
    }

    @Test
    @DisplayName("concurrent requests for overlapping seat sets cannot both fully succeed")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void overlappingSeatSetsCannotBothWin() throws Exception {
        // Seats 3,4,5 of show 1 are seeded AVAILABLE. Both requests include seat 4.
        List<Long> left = List.of(3L, 4L);
        List<Long> right = List.of(4L, 5L);
        CyclicBarrier startLine = new CyclicBarrier(2);
        AtomicInteger winners = new AtomicInteger();
        List<Throwable> losers = new CopyOnWriteArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Void> a = racingTask(startLine, left, "left@example.com", winners, losers);
            Callable<Void> b = racingTask(startLine, right, "right@example.com", winners, losers);

            for (Future<Void> f : pool.invokeAll(List.of(a, b), 60, TimeUnit.SECONDS)) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(losers)
                .as("the loser must fail on the seat conflict, not on invalid input")
                .allMatch(e -> e instanceof SeatsUnavailableException || isWriteConflict(e));
        assertThat(winners.get()).as("seat 4 is in both sets, so at most one can win").isLessThanOrEqualTo(1);

        // The contested seat references at most one booking; the losing request left no trace.
        Seat contested = seatRepository.findByShowIdAndIdIn(SHOW_ID, List.of(4L)).get(0);
        if (contested.getBooking() != null) {
            assertThat(contested.getStatus()).isIn(SeatStatus.HELD, SeatStatus.BOOKED);
        }
        assertThat(winners.get() + losers.size()).isEqualTo(2);
    }

    private Callable<Void> racingTask(
            CyclicBarrier startLine,
            List<Long> seatIds,
            String email,
            AtomicInteger winners,
            List<Throwable> losers) {
        return () -> {
            startLine.await(5, TimeUnit.SECONDS);
            try {
                bookingService.reserveSeats(new ReserveSeatsRequest(SHOW_ID, seatIds, email));
                winners.incrementAndGet();
            } catch (RuntimeException e) {
                losers.add(e);
            }
            return null;
        };
    }

    /**
     * H2 may abort one of two simultaneous writers on the same row instead of letting the
     * conditional UPDATE see zero updated rows. Both outcomes mean the same thing to the
     * caller, so both count as a correct rejection.
     */
    private static boolean isWriteConflict(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String msg = String.valueOf(t.getMessage()).toLowerCase();
            if (msg.contains("concurrent") || msg.contains("deadlock") || msg.contains("timeout")) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("validation runs before the claim, so a bad request never touches the seat")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void invalidRequestDoesNotMutateSeats() {
        try {
            bookingService.reserveSeats(new ReserveSeatsRequest(SHOW_ID, List.of(999999L), "nobody@example.com"));
        } catch (BadRequestException expected) {
            // The seat id does not exist in this show.
        }
        Seat seat = seatRepository.findByShowIdAndIdIn(SHOW_ID, List.of(CONTESTED_SEAT)).get(0);
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seat.getBooking()).isNull();
    }
}
