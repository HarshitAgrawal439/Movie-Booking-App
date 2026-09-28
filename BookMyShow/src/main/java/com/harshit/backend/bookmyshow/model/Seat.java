package com.harshit.backend.bookmyshow.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A seat <em>as it exists in one particular show</em>, not a physical seat in a hall.
 *
 * <p>Physical seats do not change between shows, but their availability does. Materialising one
 * row per (show, physical seat) means availability is a single indexed column that can be read and
 * conditionally written without joins, which is the property the reservation flow depends on.
 * The cost is row growth: {@code totalSeats * showsPerDay * cinemas} rows.
 */
@Entity
@Table(
        name = "seat",
        indexes = {
                @Index(name = "idx_seat_show_status", columnList = "show_id,status"),
                @Index(name = "idx_seat_booking", columnList = "booking_id")
        },
        uniqueConstraints = @jakarta.persistence.UniqueConstraint(
                name = "uk_seat_show_seatno", columnNames = {"show_id", "seat_no"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seat_no", nullable = false, length = 8)
    private String seatNo;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SeatStatus status;

    /** When a HELD seat becomes claimable again. Null unless {@link #status} is HELD. */
    @Column(name = "hold_expires_at")
    private LocalDateTime holdExpiresAt;

    @ManyToOne(optional = false)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @ManyToOne
    @JoinColumn(name = "booking_id")
    private Booking booking;

    public boolean isAvailableAt(LocalDateTime now) {
        return status == SeatStatus.AVAILABLE
                || (status == SeatStatus.HELD && holdExpiresAt != null && !holdExpiresAt.isAfter(now));
    }
}
