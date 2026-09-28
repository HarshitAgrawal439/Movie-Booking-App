package com.harshit.backend.bookmyshow.exception;

/**
 * At least one of the requested seats could not be claimed. Mapped to HTTP 409 Conflict.
 *
 * <p>This is a business outcome, not a server fault: the client should re-read seat availability
 * and either pick different seats or accept a partial selection.
 */
public class SeatsUnavailableException extends RuntimeException {

    public SeatsUnavailableException(String message) {
        super(message);
    }
}
