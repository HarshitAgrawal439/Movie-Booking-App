package com.harshit.backend.bookmyshow.repository;

import com.harshit.backend.bookmyshow.model.Movie;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Long> {
    @Query("SELECT m FROM Movie m JOIN Show s ON m.id = s.movie.id JOIN CinemaHall ch ON s.cinemaHall.id = ch.id JOIN Cinema c ON ch.cinema.id = c.id WHERE m.title = :title AND c.location = :city AND s.startTime BETWEEN :startOfDay AND :endOfDay")
    List<Movie> findByTitleAndCityAndDate(@Param("title") String title, @Param("city") String city, @Param("startOfDay") LocalDate startOfDay, @Param("endOfDay") LocalDate endOfDay);
}
