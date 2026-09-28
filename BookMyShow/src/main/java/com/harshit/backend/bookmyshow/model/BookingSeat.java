package com.harshit.backend.bookmyshow.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Immutable record of "this booking contained this seat".
 *
 * <p>This table exists because {@code seat.booking_id} could not carry both meanings it was being
 * asked to carry:
 *
 * <ul>
 *   <li><b>Who owns the seat right now.</b> Mutable, and must be cleared the moment a booking is
 *       cancelled or expires, otherwise the seat can never be re-claimed.
 *   <li><b>Which seats this booking contained.</b> Permanent, and must survive cancellation,
 *       because a customer looking at their history needs to see what the booking was for.
 * </ul>
 *
 * <p>One column cannot be both mutable and permanent, so the mutable half stays on
 * {@code seat.booking_id} and the permanent half lives here. Rows are inserted once, at reserve
 * time, and are never updated or deleted.
 *
 * <p>{@code seatNumber} and {@code price} are snapshots rather than joins to the live seat, so a
 * historical booking still reads correctly if a seat is ever repriced or removed.
 */
@Entity
@Table(
        name = "booking_seat",
        uniqueConstraints = @UniqueConstraint(name = "uk_booking_seat", columnNames = {"booking_id", "seat_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Column(name = "seat_number", nullable = false, length = 8)
    private String seatNumber;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
}
