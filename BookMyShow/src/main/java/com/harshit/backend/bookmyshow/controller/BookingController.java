package com.harshit.backend.bookmyshow.controller;

import com.harshit.backend.bookmyshow.dto.BookingResponse;
import com.harshit.backend.bookmyshow.dto.ReserveSeatsRequest;
import com.harshit.backend.bookmyshow.service.BookingService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * Returns 202 rather than 201: the booking is a hold, not a completed purchase. The seat is
     * not truly the user's until {@code /confirm} succeeds, and telling the client otherwise
     * invites it to treat a pending booking as final.
     */
    @PostMapping("/reserve")
    public ResponseEntity<BookingResponse> reserveSeats(@Valid @RequestBody ReserveSeatsRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(bookingService.reserveSeats(request));
    }

    @PostMapping("/{bookingId}/confirm")
    public BookingResponse confirmBooking(@PathVariable Long bookingId) {
        return bookingService.confirmBooking(bookingId);
    }

    @PostMapping("/{bookingId}/cancel")
    public BookingResponse cancelBooking(@PathVariable Long bookingId) {
        return bookingService.cancelBooking(bookingId);
    }

    @GetMapping("/{bookingId}")
    public BookingResponse getBooking(@PathVariable Long bookingId) {
        return bookingService.getBooking(bookingId);
    }

    @GetMapping
    public List<BookingResponse> getBookingsForEmail(@RequestParam String email) {
        return bookingService.getBookingsForEmail(email);
    }
}
