package com.harshit.backend.bookmyshow.repository;

import com.harshit.backend.bookmyshow.model.Show;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ShowRepository extends JpaRepository<Show, Long> {

    @Query("""
            select s from Show s
              join fetch s.movie m
              join fetch s.cinemaHall ch
              join fetch ch.cinema c
             where s.movie.id = :movieId
             order by s.startTime
            """)
    List<Show> findByMovieIdWithDetails(@Param("movieId") Long movieId);

    /**
     * Search backing {@code GET /movies/search}.
     *
     * <p>Every parameter is optional so a single endpoint serves "everything in a city today"
     * and "any show of one movie". {@code DISTINCT} matters: one movie has many shows, and
     * joining to shows/halls/cinemas would otherwise duplicate the movie row per show.
     *
     * <p>All three time bounds are {@link LocalDateTime} to match {@code Show.startTime}. An
     * earlier version of this query bound {@link java.time.LocalDate} against a
     * {@code LocalDateTime} column, which Hibernate rejects at bind time.
     */
    @Query("""
            select distinct m from Movie m
              join Show s on s.movie.id = m.id
              join s.cinemaHall ch
              join ch.cinema c
             where (:title is null or lower(m.title) like lower(concat('%', :title, '%')))
               and (:city is null or lower(c.location) = lower(:city))
               and s.startTime >= :fromTime
               and s.startTime < :toTime
            """)
    List<com.harshit.backend.bookmyshow.model.Movie> searchMovies(
            @Param("title") String title,
            @Param("city") String city,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime,
            Pageable pageable);
}
