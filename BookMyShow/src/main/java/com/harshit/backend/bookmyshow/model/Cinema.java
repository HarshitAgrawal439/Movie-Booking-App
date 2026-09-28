package com.harshit.backend.bookmyshow.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cinema", indexes = @Index(name = "idx_cinema_location", columnList = "location"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cinema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    /**
     * Free-text city. A dedicated {@code City} table would be better once a cinema can have
     * several addresses, but for a single-location cinema this avoids a join on the hottest
     * search path.
     */
    @Column(nullable = false, length = 100)
    private String location;
}
