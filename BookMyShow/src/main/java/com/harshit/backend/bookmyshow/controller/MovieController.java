package com.harshit.backend.bookmyshow.controller;

import com.harshit.backend.bookmyshow.dto.MovieResponse;
import com.harshit.backend.bookmyshow.service.MovieService;
import com.harshit.backend.bookmyshow.service.ShowService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/movies")
@Validated
public class MovieController {

    private final MovieService movieService;
    private final ShowService showService;

    public MovieController(MovieService movieService, ShowService showService) {
        this.movieService = movieService;
        this.showService = showService;
    }

    /**
     * Primary discovery endpoint. {@code title} and {@code city} are optional so the same route
     * serves "all movies in a city today" as well as an exact-title lookup.
     *
     * <p>{@code date} defaults to today, which keeps the endpoint usable without forcing the
     * client to format a date for the most common request.
     */
    @GetMapping("/search")
    public List<MovieResponse> searchMovies(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return showService.search(title, city, date == null ? LocalDate.now() : date, page, size);
    }

    @GetMapping("/by-title")
    public List<MovieResponse> searchByTitle(
            @RequestParam String title,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return movieService.searchByTitle(title, page, size);
    }
}
