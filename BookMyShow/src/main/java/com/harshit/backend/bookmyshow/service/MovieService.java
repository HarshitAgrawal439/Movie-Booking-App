package com.harshit.backend.bookmyshow.service;

import com.harshit.backend.bookmyshow.model.Movie;
import com.harshit.backend.bookmyshow.repository.MovieRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MovieService {

    private final MovieRepository movieRepository;

    public MovieService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    public List<Movie> searchMovies(String title, String city, LocalDate date) {
        LocalDate startOfDay = date.atStartOfDay().toLocalDate();
        LocalDate endOfDay = date.plusDays(1).atStartOfDay().toLocalDate();
        return movieRepository.findByTitleAndCityAndDate(title, city, startOfDay, endOfDay);
    }
}

