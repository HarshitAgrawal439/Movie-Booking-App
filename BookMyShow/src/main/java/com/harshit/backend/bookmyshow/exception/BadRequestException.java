package com.harshit.backend.bookmyshow.exception;

/** Client input failed validation. Mapped to HTTP 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
