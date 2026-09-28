package com.harshit.backend.bookmyshow.repository;

import com.harshit.backend.bookmyshow.model.Booking;
import com.harshit.backend.bookmyshow.model.Seat;
import com.harshit.backend.bookmyshow.model.SeatStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByShowIdOrderBySeatNo(Long showId);

    List<Seat> findByShowIdAndIdIn(Long showId, Collection<Long> seatIds);

    long countByShowIdAndStatus(Long showId, SeatStatus status);

    long countByShowIdAndStatusNot(Long showId, SeatStatus status);

    @Query("select coalesce(sum(s.price), 0) from Seat s where s.show.id = :showId and s.id in :seatIds")
    BigDecimal sumPriceByShowIdAndIds(@Param("showId") Long showId, @Param("seatIds") Collection<Long> seatIds);

    /**
     * Atomically claims seats for a booking.
     *
     * <p>This is the whole concurrency story. The {@code WHERE} clause carries the precondition
     * (seat is free, or its hold has lapsed) and the database applies the check and the write as
     * one indivisible statement. Two concurrent requests for the same seat both read
     * "available", but only the first can turn its row into HELD, so the second gets 0 updated
     * rows for that seat and loses. No locks, no isolation-level escalation, no retry loop.
     *
     * <p>{@code seat_ids.size() != updated} means the caller lost a race and must roll back.
     *
     * <p>Note the parameter is the {@link Booking} entity, not its id. Assigning an
     * association needs the association; only the read side ({@code s.booking.id = ...})
     * can be compared against a bare id.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Seat s
               set s.status = :heldStatus,
                   s.holdExpiresAt = :holdExpiresAt,
                   s.booking = :booking
             where s.show.id = :showId
               and s.id in :seatIds
               and (s.status = :availableStatus
                    or (s.status = :heldStatus and s.holdExpiresAt <= :now))
            """)
    int claimSeats(
            @Param("showId") Long showId,
            @Param("seatIds") Collection<Long> seatIds,
            @Param("booking") Booking booking,
            @Param("heldStatus") SeatStatus heldStatus,
            @Param("availableStatus") SeatStatus availableStatus,
            @Param("holdExpiresAt") LocalDateTime holdExpiresAt,
            @Param("now") LocalDateTime now);

    /** Converts a lapsed hold back into a free seat. Scoped to one booking to stay idempotent. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Seat s
               set s.status = :availableStatus, s.holdExpiresAt = null, s.booking = null
             where s.booking.id = :bookingId and s.status = :heldStatus
            """)
    int releaseHolds(
            @Param("bookingId") Long bookingId,
            @Param("heldStatus") SeatStatus heldStatus,
            @Param("availableStatus") SeatStatus availableStatus);

    /** Seats whose hold window elapsed. Called by the expiry sweeper. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Seat s
               set s.status = :availableStatus, s.holdExpiresAt = null, s.booking = null
             where s.status = :heldStatus and s.holdExpiresAt <= :now
            """)
    int releaseExpiredHolds(
            @Param("heldStatus") SeatStatus heldStatus,
            @Param("availableStatus") SeatStatus availableStatus,
            @Param("now") LocalDateTime now);

    /**
     * Promotes held seats to booked once payment succeeds.
     *
     * <p>The {@code holdExpiresAt > :now} guard is what makes "you cannot pay for a window
     * that has already closed" true even if the sweeper has not run yet. Without it, a user
     * who pays 2 seconds late would still be confirmed, and the row count check in the
     * service would report success.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Seat s
               set s.status = :bookedStatus, s.holdExpiresAt = null
             where s.booking.id = :bookingId
               and s.status = :heldStatus
               and s.holdExpiresAt > :now
            """)
    int confirmSeats(
            @Param("bookingId") Long bookingId,
            @Param("heldStatus") SeatStatus heldStatus,
            @Param("bookedStatus") SeatStatus bookedStatus,
            @Param("now") LocalDateTime now);
}
