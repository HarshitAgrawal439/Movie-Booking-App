package com.harshit.backend.bookmyshow.exception;

import java.time.LocalDateTime;
import java.util.List;

/** Uniform error body. Keeps clients from having to parse Spring's default error shape. */
public record ApiError(LocalDateTime timestamp, int status, String error, String message, String path, List<String> details) {

    public static ApiError of(int status, String error, String message, String path, List<String> details) {
        return new ApiError(LocalDateTime.now(), status, error, message, path, details);
    }
}
