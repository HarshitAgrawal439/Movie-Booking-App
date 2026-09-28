package com.harshit.backend.bookmyshow.controller;

import com.harshit.backend.bookmyshow.dto.ShowResponse;
import com.harshit.backend.bookmyshow.service.ShowService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shows")
public class ShowController {

    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    @GetMapping("/movie/{movieId}")
    public List<ShowResponse> getShowsByMovieId(@PathVariable Long movieId) {
        return showService.getShowsByMovieId(movieId);
    }
}
