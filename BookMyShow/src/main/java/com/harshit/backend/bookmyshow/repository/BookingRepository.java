package com.harshit.backend.bookmyshow.repository;

import com.harshit.backend.bookmyshow.model.Booking;
import com.harshit.backend.bookmyshow.model.BookingStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByEmailOrderByCreatedOnDesc(String email);

    boolean existsByBookingNumber(String bookingNumber);

    /** Bookings whose payment window elapsed without confirmation. Called by the expiry sweeper. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Booking b
               set b.status = :expiredStatus, b.updatedOn = :now
             where b.status = :pendingStatus and b.updatedOn <= :cutoff
            """)
    int expireStaleBookings(
            @Param("pendingStatus") BookingStatus pendingStatus,
            @Param("expiredStatus") BookingStatus expiredStatus,
            @Param("now") LocalDateTime now,
            @Param("cutoff") LocalDateTime cutoff);

    @Query("select b from Booking b join fetch b.show s join fetch s.movie where b.id = :id")
    java.util.Optional<Booking> findByIdWithShowAndMovie(@Param("id") Long id);

    @Query("select b from Booking b where b.id in :ids")
    List<Booking> findAllByIdIn(@Param("ids") Collection<Long> ids);

    /**
     * Compare-and-set on the booking status. Returns 1 if this caller performed the transition,
     * 0 if the row was no longer in {@code expectedStatus}.
     *
     * <p>This is the reason confirm, cancel and the sweeper cannot corrupt each other. An
     * in-memory {@code if (status == PENDING)} check is worthless under concurrency: three
     * threads can all read PENDING and then all write. Folding the precondition into the UPDATE
     * makes the database the arbiter, exactly like the seat claim does.
     *
     * <p>{@code clearAutomatically} detaches the entity the caller loaded for its error message,
     * so the service must re-read the booking if it needs a fresh view afterwards.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Booking b
               set b.status = :newStatus, b.updatedOn = :now
             where b.id = :id and b.status = :expectedStatus
            """)
    int transitionStatus(
            @Param("id") Long id,
            @Param("expectedStatus") BookingStatus expectedStatus,
            @Param("newStatus") BookingStatus newStatus,
            @Param("now") LocalDateTime now);
}
