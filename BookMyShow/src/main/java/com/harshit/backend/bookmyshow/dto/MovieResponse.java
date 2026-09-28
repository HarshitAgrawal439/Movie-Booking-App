package com.harshit.backend.bookmyshow.dto;

import com.harshit.backend.bookmyshow.model.Movie;
import java.time.LocalDate;

public record MovieResponse(
        Long id, String title, String description, int duration, String language, LocalDate releaseDate, String genre) {

    public static MovieResponse from(Movie m) {
        return new MovieResponse(
                m.getId(), m.getTitle(), m.getDescription(), m.getDuration(),
                m.getLanguage(), m.getReleaseDate(), m.getGenre());
    }
}
