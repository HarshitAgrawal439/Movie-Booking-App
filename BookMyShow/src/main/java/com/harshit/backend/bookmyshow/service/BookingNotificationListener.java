package com.harshit.backend.bookmyshow.service;

import com.harshit.backend.bookmyshow.dto.BookingResponse;
import com.harshit.backend.bookmyshow.event.BookingConfirmedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the confirmation email.
 *
 * <p>Two annotations carry the design:
 *
 * <ul>
 *   <li>{@code @TransactionalEventListener(AFTER_COMMIT)} - the booking must be durable before
 *       the user is told it succeeded. With a plain {@code @EventListener} an email would go out
 *       for a transaction that later rolled back.
 *   <li>{@code @Async} - an SMTP call can take seconds; the user should not hold the HTTP
 *       connection while it happens.
 * </ul>
 */
@Component
public class BookingNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(BookingNotificationListener.class);

    private final BookingService bookingService;

    public BookingNotificationListener(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        // Stand-in for a real mail sender. Kept as a real lookup so tests can assert the
        // rendered confirmation, and so swapping in JavaMailSender is a one-line change.
        try {
            BookingResponse booking = bookingService.getBooking(event.bookingId());
            log.info(
                    "Confirmation email to {}: booking {} for {} at {} on {} in {}, seats {}, total {}",
                    event.email(),
                    booking.bookingNumber(),
                    booking.movieTitle(),
                    booking.showStartTime(),
                    booking.cinemaName(),
                    booking.hallName(),
                    booking.seatNumbers(),
                    booking.totalPrice());
        } catch (RuntimeException e) {
            // A failed notification must never surface as a failed booking.
            log.error("Could not build confirmation email for booking {}", event.bookingNumber(), e);
        }
    }
}
