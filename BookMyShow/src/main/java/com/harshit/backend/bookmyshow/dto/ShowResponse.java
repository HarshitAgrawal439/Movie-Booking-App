package com.harshit.backend.bookmyshow.dto;

import com.harshit.backend.bookmyshow.model.Show;
import java.time.LocalDateTime;

public record ShowResponse(
        Long id,
        String movieTitle,
        String cinemaName,
        String cinemaLocation,
        String hallName,
        LocalDateTime startTime,
        LocalDateTime endTime) {

    public static ShowResponse from(Show s) {
        return new ShowResponse(
                s.getId(),
                s.getMovie().getTitle(),
                s.getCinemaHall().getCinema().getName(),
                s.getCinemaHall().getCinema().getLocation(),
                s.getCinemaHall().getName(),
                s.getStartTime(),
                s.getEndTime());
    }
}
