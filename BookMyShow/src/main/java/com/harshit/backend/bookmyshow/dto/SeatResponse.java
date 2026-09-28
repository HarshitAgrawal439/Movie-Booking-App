package com.harshit.backend.bookmyshow.dto;

import com.harshit.backend.bookmyshow.model.Seat;
import com.harshit.backend.bookmyshow.model.SeatStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @param holdExpiresAt non-null only while {@code status} is HELD, so the client can show a
 *                      countdown and stop counting down on its own.
 */
public record SeatResponse(
        Long id, String seatNo, BigDecimal price, SeatStatus status, LocalDateTime holdExpiresAt) {

    public static SeatResponse from(Seat s) {
        return new SeatResponse(s.getId(), s.getSeatNo(), s.getPrice(), s.getStatus(), s.getHoldExpiresAt());
    }
}
