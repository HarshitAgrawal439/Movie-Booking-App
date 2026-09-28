package com.harshit.backend.bookmyshow.model;

/**
 * Lifecycle of a single seat within one show.
 *
 * <p>A boolean {@code isReserved} is not enough here, because a seat can be temporarily
 * blocked while the user is on the payment page. Modelling that as a boolean loses the
 * distinction between "definitely sold" and "maybe sold, expires in 4 minutes", which is
 * exactly the distinction that makes seat-holds possible.
 */
public enum SeatStatus {
    AVAILABLE,
    /** Blocked for a user who is mid-payment. Released by {@code holdExpiresAt}. */
    HELD,
    /** Paid for. Terminal state. */
    BOOKED
}
