package com.harshit.backend.bookmyshow.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "booking")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable reference handed to the customer, e.g. BMS7K2QX4. */
    @Column(name = "booking_number", nullable = false, unique = true, length = 16)
    private String bookingNumber;

    @Column(nullable = false)
    private String email;

    @Column(name = "number_of_seats", nullable = false)
    private int numberOfSeats;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BookingStatus status;

    @Column(name = "created_on", nullable = false)
    private LocalDateTime createdOn;

    @Column(name = "updated_on", nullable = false)
    private LocalDateTime updatedOn;

    @ManyToOne(optional = false)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    /**
     * Which seats this booking contains. Backed by {@code booking_seat}, not by
     * {@code seat.booking_id}.
     *
     * <p>{@code seat.booking_id} answers "who owns this seat right now" and is cleared on
     * cancel or expiry; this collection answers "what did this booking contain" and is permanent.
     * The old design used the seat's own foreign key for both, which meant cancelling a booking
     * erased the seat list from the customer's history.
     *
     * <p>Always fetched explicitly via {@code BookingSeatRepository} rather than navigated here,
     * so mapping to a DTO cannot trigger a lazy load after the session closes.
     */
    @OneToMany(mappedBy = "booking")
    @Builder.Default
    private List<BookingSeat> seatEntries = new ArrayList<>();
}
