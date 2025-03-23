package com.harshit.backend.bookmyshow.service;

import com.harshit.backend.bookmyshow.model.Booking;
import com.harshit.backend.bookmyshow.model.Seat;
import com.harshit.backend.bookmyshow.repository.BookingRepository;
import com.harshit.backend.bookmyshow.repository.SeatRepository;
import com.harshit.backend.bookmyshow.repository.ShowRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final ShowRepository showRepository;

    public BookingService(BookingRepository bookingRepository, SeatRepository seatRepository, ShowRepository showRepository) {
        this.bookingRepository = bookingRepository;
        this.seatRepository = seatRepository;
        this.showRepository = showRepository;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public String reserveSeats(Long showId, List<Long> seatIds) {
        List<Seat> seats = seatRepository.findAllById(seatIds);

        for (Seat seat : seats) {
            if (seat.isReserved()) {
                throw new RuntimeException("Seat " + seat.getSeatNo() + " is already reserved");
            }
            seat.setReserved(true);
        }

        seatRepository.saveAll(seats);

        Booking booking = new Booking();
        booking.setShow(showRepository.findById(showId).orElseThrow(() -> new RuntimeException("Show not found")));
        booking.setNumberOfSeats(seatIds.size());
        booking.setCreatedOn(LocalDateTime.now());
        booking.setStatus("PENDING");

        bookingRepository.save(booking);

        return "Reservation successful. Please complete the payment.";
    }

    public void sendEmailOnBookingSuccess(String email) {
        // Implement email sending here
    }
}