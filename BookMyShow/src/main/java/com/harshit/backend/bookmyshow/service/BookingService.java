package com.harshit.backend.bookmyshow.service;

import com.harshit.backend.bookmyshow.dto.BookingResponse;
import com.harshit.backend.bookmyshow.dto.ReserveSeatsRequest;
import com.harshit.backend.bookmyshow.event.BookingConfirmedEvent;
import com.harshit.backend.bookmyshow.exception.BadRequestException;
import com.harshit.backend.bookmyshow.exception.InvalidBookingStateException;
import com.harshit.backend.bookmyshow.exception.ResourceNotFoundException;
import com.harshit.backend.bookmyshow.exception.SeatsUnavailableException;
import com.harshit.backend.bookmyshow.model.Booking;
import com.harshit.backend.bookmyshow.model.BookingSeat;
import com.harshit.backend.bookmyshow.model.BookingStatus;
import com.harshit.backend.bookmyshow.model.Seat;
import com.harshit.backend.bookmyshow.model.SeatStatus;
import com.harshit.backend.bookmyshow.model.Show;
import com.harshit.backend.bookmyshow.repository.BookingRepository;
import com.harshit.backend.bookmyshow.repository.BookingSeatRepository;
import com.harshit.backend.bookmyshow.repository.SeatRepository;
import com.harshit.backend.bookmyshow.repository.ShowRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class BookingService {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int BOOKING_NUMBER_LENGTH = 8;

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final SeatRepository seatRepository;
    private final ShowRepository showRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final long holdMinutes;

    public BookingService(
            BookingRepository bookingRepository,
            BookingSeatRepository bookingSeatRepository,
            SeatRepository seatRepository,
            ShowRepository showRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            @Value("${booking.hold-minutes:5}") long holdMinutes) {
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.seatRepository = seatRepository;
        this.showRepository = showRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.holdMinutes = holdMinutes;
    }

    /**
     * Places a time-boxed hold on the requested seats.
     *
     * <p>Transaction boundary lives here, in the service, because a booking is one business
     * transaction: booking row insert, seat claim, and price snapshot must all commit or none
     * of them may. The default READ COMMITTED isolation is sufficient precisely because the
     * seat claim is a conditional UPDATE rather than a read-then-write.
     *
     * <p>Deliberately <b>not</b> SERIALIZABLE: see {@code ../docs/ARCHITECTURE.md} for why that
     * trades a real throughput problem for a bug that a single atomic statement already solves.
     */
    @Transactional
    public BookingResponse reserveSeats(ReserveSeatsRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> seatIds = request.seatIds().stream().distinct().toList();

        Show show = showRepository
                .findById(request.showId())
                .orElseThrow(() -> new ResourceNotFoundException("Show " + request.showId() + " not found"));

        if (!show.getStartTime().isAfter(now)) {
            throw new BadRequestException("Show " + show.getId() + " has already started");
        }

        // Guards against a client passing seat ids from a different show. Without this the
        // claim below would happily reserve another show's seats under this show's booking.
        List<Seat> requested = seatRepository.findByShowIdAndIdIn(show.getId(), seatIds);
        if (requested.size() != seatIds.size()) {
            List<Long> found = requested.stream().map(Seat::getId).toList();
            List<Long> foreign = seatIds.stream().filter(id -> !found.contains(id)).toList();
            throw new BadRequestException("Seat ids do not belong to show " + show.getId() + ": " + foreign);
        }

        BigDecimal totalPrice = seatRepository.sumPriceByShowIdAndIds(show.getId(), seatIds);
        LocalDateTime holdExpiresAt = now.plusMinutes(holdMinutes);

        Booking booking = bookingRepository.save(Booking.builder()
                .bookingNumber(generateBookingNumber())
                .email(request.email())
                .numberOfSeats(seatIds.size())
                .totalPrice(totalPrice)
                .status(BookingStatus.PENDING)
                .show(show)
                .createdOn(now)
                .updatedOn(now)
                .build());

        int claimed = seatRepository.claimSeats(
                show.getId(), seatIds, booking, SeatStatus.HELD, SeatStatus.AVAILABLE, holdExpiresAt, now);

        if (claimed != seatIds.size()) {
            // Throwing rolls the whole transaction back, so the partial claim is undone.
            // The client is told nothing about which seats won; that is deliberate, since
            // exposing it invites a retry loop that hammers the endpoint.
            throw new SeatsUnavailableException(
                    "Could not reserve all requested seats; some are already taken. Refresh availability and retry.");
        }

        // Written only once the claim is known to have succeeded, and never mutated afterwards.
        // This is the record that survives cancellation.
        recordSeatHistory(booking.getId(), seatIds);

        return loadBooking(booking.getId());
    }

    @Transactional
    public BookingResponse confirmBooking(Long bookingId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Booking booking = loadBookingEntity(bookingId);

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new InvalidBookingStateException(
                    "Booking " + booking.getBookingNumber() + " is " + booking.getStatus() + " and cannot be confirmed");
        }

        // Claim the state transition before touching seats. If a cancel or the sweeper wins the
        // race, this returns 0 and we bail out without having released or confirmed anything.
        if (bookingRepository.transitionStatus(bookingId, BookingStatus.PENDING, BookingStatus.CONFIRMED, now) == 0) {
            throw new InvalidBookingStateException(
                    "Booking " + booking.getBookingNumber() + " is no longer pending; it changed while the request was in flight");
        }

        int confirmed = seatRepository.confirmSeats(bookingId, SeatStatus.HELD, SeatStatus.BOOKED, now);
        if (confirmed != booking.getNumberOfSeats()) {
            // Fewer rows than seats means at least one hold lapsed between the two statements.
            // The transition above is rolled back with the transaction, so the booking never ends
            // up CONFIRMED with only some of its seats booked.
            throw new InvalidBookingStateException(
                    "Hold on booking " + booking.getBookingNumber() + " has expired; payment cannot be applied");
        }

        publishAfterCommit(() ->
                eventPublisher.publishEvent(new BookingConfirmedEvent(bookingId, booking.getBookingNumber(), booking.getEmail())));
        return loadBooking(bookingId);
    }

    @Transactional
    public BookingResponse cancelBooking(Long bookingId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Booking booking = loadBookingEntity(bookingId);

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            throw new InvalidBookingStateException(
                    "Booking " + booking.getBookingNumber() + " is confirmed and cannot be cancelled");
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new InvalidBookingStateException(
                    "Booking " + booking.getBookingNumber() + " is already " + booking.getStatus());
        }

        if (bookingRepository.transitionStatus(bookingId, BookingStatus.PENDING, BookingStatus.CANCELLED, now) == 0) {
            throw new InvalidBookingStateException(
                    "Booking " + booking.getBookingNumber() + " is no longer pending; it changed while the request was in flight");
        }

        seatRepository.releaseHolds(bookingId, SeatStatus.HELD, SeatStatus.AVAILABLE);

        return loadBooking(bookingId);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBooking(Long bookingId) {
        return loadBooking(bookingId);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsForEmail(String email) {
        List<Booking> bookings = bookingRepository.findByEmailOrderByCreatedOnDesc(email);
        if (bookings.isEmpty()) {
            return List.of();
        }
        Map<Long, List<BookingSeat>> entriesByBooking =
                bookingSeatRepository.findByBookingIdInWithSeat(bookings.stream().map(Booking::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(entry -> entry.getBooking().getId()));
        return bookings.stream()
                .map(b -> BookingResponse.from(b, entriesByBooking.getOrDefault(b.getId(), List.of())))
                .toList();
    }

    /**
     * Snapshots the seat numbers and prices into {@code booking_seat}. Written inside the same
     * transaction as the claim, so a booking can never exist without its seat record.
     */
    private void recordSeatHistory(Long bookingId, List<Long> seatIds) {
        Booking booking = bookingRepository
                .findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking " + bookingId + " not found"));
        List<Seat> seats = seatRepository.findByShowIdAndIdIn(booking.getShow().getId(), seatIds);
        List<BookingSeat> entries = seats.stream()
                .map(seat -> BookingSeat.builder()
                        .booking(booking)
                        .seat(seat)
                        .seatNumber(seat.getSeatNo())
                        .price(seat.getPrice())
                        .build())
                .toList();
        bookingSeatRepository.saveAll(entries);
    }

    private BookingResponse loadBooking(Long bookingId) {
        Booking booking = loadBookingEntity(bookingId);
        return BookingResponse.from(booking, bookingSeatRepository.findByBookingIdWithSeat(bookingId));
    }

    /**
     * Loads the booking with its seats and show graph attached. {@code seats} is LAZY, so
     * reading it outside the transaction would throw LazyInitializationException during
     * response serialisation.
     */
    private Booking loadBookingEntity(Long bookingId) {
        return bookingRepository
                .findByIdWithShowAndMovie(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking " + bookingId + " not found"));
    }

    /**
     * Notification must not be sent for a transaction that later rolls back, so the event is
     * deferred until after commit rather than dispatched inline.
     */
    private void publishAfterCommit(Runnable publish) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }

    private String generateBookingNumber() {
        String candidate;
        do {
            StringBuilder sb = new StringBuilder("BMS");
            for (int i = 0; i < BOOKING_NUMBER_LENGTH; i++) {
                sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
            }
            candidate = sb.toString();
        } while (bookingRepository.existsByBookingNumber(candidate));
        return candidate;
    }
}
