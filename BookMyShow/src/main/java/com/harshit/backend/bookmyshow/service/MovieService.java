package com.harshit.backend.bookmyshow.service;

import com.harshit.backend.bookmyshow.dto.MovieResponse;
import com.harshit.backend.bookmyshow.repository.MovieRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MovieService {

    private final MovieRepository movieRepository;

    public MovieService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    /**
     * Title-only lookup. The city+date-aware search lives in {@link ShowService} because it has
     * to traverse Show -> CinemaHall -> Cinema; keeping both in one place would duplicate the join.
     */
    @Transactional(readOnly = true)
    public List<MovieResponse> searchByTitle(String title, int page, int size) {
        if (!StringUtils.hasText(title)) {
            return List.of();
        }
        return movieRepository.searchByTitle(title.trim(), PageRequest.of(page, size))
                .stream()
                .map(MovieResponse::from)
                .toList();
    }
}
