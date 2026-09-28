package com.harshit.backend.bookmyshow.event;

/**
 * Published after a booking transaction has committed, carrying only identifiers.
 *
 * <p>Passing the id rather than the entity is deliberate: an async listener runs outside the
 * originating transaction and persistence context, so a detached entity reference is both a
 * correctness hazard and a leak of the whole object graph.
 */
public record BookingConfirmedEvent(Long bookingId, String bookingNumber, String email) {
}
