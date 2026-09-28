package com.harshit.backend.bookmyshow.repository;

import com.harshit.backend.bookmyshow.model.Movie;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Long> {

    @Query("""
            select m from Movie m
             where lower(m.title) like lower(concat('%', :title, '%'))
             order by m.title
            """)
    List<Movie> searchByTitle(@Param("title") String title, Pageable pageable);
}
