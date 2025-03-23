package com.harshit.backend.bookmyshow.controller;

import com.harshit.backend.bookmyshow.model.Movie;
import com.harshit.backend.bookmyshow.service.MovieService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/movies")
public class MovieController {
    private final MovieService movieService;

    public MovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    @GetMapping("/search")
    public List<Movie> searchMovies(@RequestParam String title, @RequestParam String city, @RequestParam LocalDate date) {
        return movieService.searchMovies(title, city, date);
    }
}