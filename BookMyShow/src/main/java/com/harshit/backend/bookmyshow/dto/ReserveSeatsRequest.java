package com.harshit.backend.bookmyshow.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ReserveSeatsRequest(
        @NotNull(message = "showId is required") Long showId,
        @NotEmpty(message = "at least one seat must be selected")
                @Size(max = 10, message = "cannot book more than 10 seats in one booking")
                List<Long> seatIds,
        @NotNull @Email(message = "a valid email is required") String email) {
}
