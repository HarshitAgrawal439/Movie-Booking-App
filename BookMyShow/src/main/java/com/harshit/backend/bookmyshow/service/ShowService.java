package com.harshit.backend.bookmyshow.service;

import com.harshit.backend.bookmyshow.dto.MovieResponse;
import com.harshit.backend.bookmyshow.dto.ShowResponse;
import com.harshit.backend.bookmyshow.exception.ResourceNotFoundException;
import com.harshit.backend.bookmyshow.repository.ShowRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ShowService {

    private final ShowRepository showRepository;

    public ShowService(ShowRepository showRepository) {
        this.showRepository = showRepository;
    }

    @Transactional(readOnly = true)
    public List<MovieResponse> search(String title, String city, LocalDate date, int page, int size) {
        LocalDateTime from = date.atStartOfDay();
        // Half-open [from, to) so a show starting exactly at midnight tomorrow is not
        // pulled into today's results by an inclusive BETWEEN.
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        var pageable = PageRequest.of(page, size, Sort.by("title"));
        return showRepository.searchMovies(
                        StringUtils.hasText(title) ? title.trim() : null,
                        StringUtils.hasText(city) ? city.trim() : null,
                        from,
                        to,
                        pageable)
                .stream()
                .map(MovieResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ShowResponse> getShowsByMovieId(Long movieId) {
        return showRepository.findByMovieIdWithDetails(movieId).stream()
                .map(ShowResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public void requireExists(Long showId) {
        if (!showRepository.existsById(showId)) {
            throw new ResourceNotFoundException("Show " + showId + " not found");
        }
    }
}
