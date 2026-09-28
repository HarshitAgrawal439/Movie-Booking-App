package com.harshit.backend.bookmyshow.exception;

/** Booking is not in a state that allows the requested transition. Mapped to HTTP 409. */
public class InvalidBookingStateException extends RuntimeException {

    public InvalidBookingStateException(String message) {
        super(message);
    }
}
