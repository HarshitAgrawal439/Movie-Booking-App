package com.harshit.backend.bookmyshow.dto;

import com.harshit.backend.bookmyshow.model.Booking;
import com.harshit.backend.bookmyshow.model.BookingSeat;
import com.harshit.backend.bookmyshow.model.BookingStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BookingResponse(
        Long id,
        String bookingNumber,
        Long showId,
        String movieTitle,
        String cinemaName,
        String hallName,
        String cinemaLocation,
        LocalDateTime showStartTime,
        List<String> seatNumbers,
        int numberOfSeats,
        BigDecimal totalPrice,
        BookingStatus status,
        LocalDateTime holdExpiresAt,
        String email,
        LocalDateTime createdOn) {

    /**
     * Seat numbers come from the {@code booking_seat} rows, not from the seats that currently
     * point back at this booking. A cancelled or expired booking therefore still reports the
     * seats it was for; reading them off the seat rows would report an empty list, because
     * releasing a hold is exactly what clears those links.
     *
     * @param entries the booking's seat entries, with their seats already fetched
     */
    public static BookingResponse from(Booking b, List<BookingSeat> entries) {
        List<String> seatNumbers = entries.stream()
                .map(BookingSeat::getSeatNumber)
                .sorted()
                .toList();
        // The hold deadline is live state, not history, so it is read off the seats themselves.
        // Released and confirmed seats have no deadline, which is exactly what should be shown.
        LocalDateTime holdExpiresAt = entries.stream()
                .map(entry -> entry.getSeat().getHoldExpiresAt())
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);

        var show = b.getShow();
        return new BookingResponse(
                b.getId(),
                b.getBookingNumber(),
                show.getId(),
                show.getMovie().getTitle(),
                show.getCinemaHall().getCinema().getName(),
                show.getCinemaHall().getName(),
                show.getCinemaHall().getCinema().getLocation(),
                show.getStartTime(),
                seatNumbers,
                b.getNumberOfSeats(),
                b.getTotalPrice(),
                b.getStatus(),
                holdExpiresAt,
                b.getEmail(),
                b.getCreatedOn());
    }
}
