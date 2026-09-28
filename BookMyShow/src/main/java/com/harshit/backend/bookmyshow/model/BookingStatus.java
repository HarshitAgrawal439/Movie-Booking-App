package com.harshit.backend.bookmyshow.model;

public enum BookingStatus {
    /** Seats are held for the user, payment not yet completed. */
    PENDING,
    /** Payment succeeded, seats are permanently booked. */
    CONFIRMED,
    /** Payment failed or the user abandoned the flow; seats were released. */
    CANCELLED,
    /** Hold window elapsed without payment; seats were released. */
    EXPIRED
}
