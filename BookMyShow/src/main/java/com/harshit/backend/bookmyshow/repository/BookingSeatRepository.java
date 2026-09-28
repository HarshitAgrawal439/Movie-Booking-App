package com.harshit.backend.bookmyshow.repository;

import com.harshit.backend.bookmyshow.model.BookingSeat;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {

    /**
     * Entries for one booking with their seats attached.
     *
     * <p>{@code join fetch e.seat} rather than lazy navigation: the mapping to
     * {@code BookingResponse} happens after the persistence context is gone, and
     * {@code open-in-view=false} means a lazy access here would throw.
     */
    @Query("select e from BookingSeat e join fetch e.seat where e.booking.id = :bookingId order by e.seatNumber")
    List<BookingSeat> findByBookingIdWithSeat(@Param("bookingId") Long bookingId);

    /** One query for a whole page of bookings, so listing by email is not an N+1. */
    @Query("select e from BookingSeat e join fetch e.seat where e.booking.id in :bookingIds order by e.seatNumber")
    List<BookingSeat> findByBookingIdInWithSeat(@Param("bookingIds") Collection<Long> bookingIds);
}
