package com.harshit.backend.bookmyshow.repository;

import com.harshit.backend.bookmyshow.model.Movie;
import com.harshit.backend.bookmyshow.model.Show;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShowRepository extends JpaRepository<Show, Long> {
    List<Show> findByMovieId(Long movieId);
    List<Show> findByMovie(Movie movie);
}