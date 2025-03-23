package com.harshit.backend.bookmyshow.controller;

import com.harshit.backend.bookmyshow.service.BookingService;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PostMapping("/reserve")
    public String reserveSeats(@RequestParam Long showId, @RequestParam List<Long> seatIds) {
        return bookingService.reserveSeats(showId, seatIds);
    }
}
