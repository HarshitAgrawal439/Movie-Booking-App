package com.harshit.backend.bookmyshow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.harshit.backend.bookmyshow.dto.BookingResponse;
import com.harshit.backend.bookmyshow.dto.ReserveSeatsRequest;
import com.harshit.backend.bookmyshow.model.BookingStatus;
import com.harshit.backend.bookmyshow.repository.SeatRepository;
import com.harshit.backend.bookmyshow.repository.ShowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Walks the whole customer journey through the HTTP layer: discover, choose a show, see seats,
 * hold them, pay, then read the booking back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingFlowIntegrationTest extends AbstractBookingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ShowRepository showRepository;

    @Test
    @DisplayName("search returns movies playing today in a city")
    void searchFindsMoviesForToday() throws Exception {
        mockMvc.perform(get("/movies/search").param("city", "New York").param("date", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(org.hamcrest.Matchers.greaterThan(0))))
                .andExpect(jsonPath("$[0].title").exists())
                .andExpect(jsonPath("$[0].id").exists());
    }

    @Test
    @DisplayName("search is case-insensitive on city and partial on title")
    void searchIsForgiving() throws Exception {
        mockMvc.perform(get("/movies/search")
                        .param("title", "incep")
                        .param("city", "new york")
                        .param("date", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Inception"));
    }

    @Test
    @DisplayName("search for a date with no shows returns an empty list, not an error")
    void searchWithNoResultsIsEmptyNotError() throws Exception {
        mockMvc.perform(get("/movies/search").param("city", "New York").param("date", "1999-01-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("shows for a movie include cinema and timing details")
    void listsShowsForMovie() throws Exception {
        mockMvc.perform(get("/shows/movie/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].movieTitle").value("Inception"))
                .andExpect(jsonPath("$[0].cinemaName").value("Cineplex A"))
                .andExpect(jsonPath("$[0].hallName").exists())
                .andExpect(jsonPath("$[0].startTime").exists());
    }

    @Test
    @DisplayName("seat map shows taken seats as BOOKED and free ones as AVAILABLE")
    void seatMapReflectsAvailability() throws Exception {
        mockMvc.perform(get("/seats/show/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(8)))
                .andExpect(jsonPath("$[0].seatNo").value("A1"))
                .andExpect(jsonPath("$[0].status").value("BOOKED"))
                .andExpect(jsonPath("$[2].status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("full flow: hold seats, confirm, then read back a CONFIRMED booking")
    void reserveThenConfirm() throws Exception {
        // Seats 3,4 (A3,A4) of show 1 are seeded AVAILABLE.
        MvcResult reserveResult = mockMvc.perform(post("/bookings/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ReserveSeatsRequest(1L, java.util.List.of(3L, 4L), "carol@example.com"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.numberOfSeats").value(2))
                .andExpect(jsonPath("$.seatNumbers", org.hamcrest.Matchers.containsInAnyOrder("A3", "A4")))
                .andExpect(jsonPath("$.holdExpiresAt").exists())
                .andExpect(jsonPath("$.bookingNumber").exists())
                .andReturn();

        BookingResponse reserved = objectMapper.readValue(reserveResult.getResponse().getContentAsString(), BookingResponse.class);
        assertThat(reserved.bookingNumber()).startsWith("BMS");
        // 2 x 300.00, compared numerically: BigDecimal.equals is scale-sensitive, so
        // isEqualByComparingTo rather than a JsonPath .value(600.0) which would compare
        // against a Double.
        assertThat(reserved.totalPrice()).isEqualByComparingTo("600.00");

        mockMvc.perform(post("/bookings/{id}/confirm", reserved.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.holdExpiresAt").doesNotExist());

        mockMvc.perform(get("/bookings/{id}", reserved.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // Seats are now terminally BOOKED and no longer re-claimable.
        assertThat(seatRepository.findByShowIdAndIdIn(1L, java.util.List.of(3L, 4L)))
                .allMatch(s -> s.getStatus() == com.harshit.backend.bookmyshow.model.SeatStatus.BOOKED);
    }

    @Test
    @DisplayName("cancelling a pending booking returns its seats to the pool")
    void cancelReleasesSeats() throws Exception {
        MvcResult reserveResult = mockMvc.perform(post("/bookings/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ReserveSeatsRequest(1L, java.util.List.of(5L, 6L), "dan@example.com"))))
                .andExpect(status().isAccepted())
                .andReturn();

        BookingResponse reserved = objectMapper.readValue(reserveResult.getResponse().getContentAsString(), BookingResponse.class);

        mockMvc.perform(post("/bookings/{id}/cancel", reserved.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(seatRepository.findByShowIdAndIdIn(1L, java.util.List.of(5L, 6L)))
                .allMatch(s -> s.getStatus() == com.harshit.backend.bookmyshow.model.SeatStatus.AVAILABLE);
    }

    @Test
    @DisplayName("a second attempt on an already-held seat is rejected with 409")
    void doubleBookingIsRejected() throws Exception {
        mockMvc.perform(post("/bookings/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ReserveSeatsRequest(1L, java.util.List.of(7L), "first@example.com"))))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/bookings/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ReserveSeatsRequest(1L, java.util.List.of(7L), "second@example.com"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("reserving a seat from a different show is rejected with 400")
    void crossShowSeatIsRejected() throws Exception {
        // Seat 9 belongs to show 2, but the request claims show 1.
        mockMvc.perform(post("/bookings/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ReserveSeatsRequest(1L, java.util.List.of(9L), "eve@example.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("do not belong")));
    }

    @Test
    @DisplayName("unknown show is a 404, not a 500")
    void unknownShowIsNotFound() throws Exception {
        mockMvc.perform(post("/bookings/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ReserveSeatsRequest(99999L, java.util.List.of(1L), "frank@example.com"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("invalid payload is a 400 with field-level detail")
    void validationErrorsAreReported() throws Exception {
        mockMvc.perform(post("/bookings/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReserveSeatsRequest(1L, java.util.List.of(), "not-an-email"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    @DisplayName("confirming a booking twice is a 409, not a double charge")
    void confirmIsNotIdempotentSilently() throws Exception {
        MvcResult reserveResult = mockMvc.perform(post("/bookings/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ReserveSeatsRequest(1L, java.util.List.of(8L), "gina@example.com"))))
                .andExpect(status().isAccepted())
                .andReturn();

        BookingResponse reserved = objectMapper.readValue(reserveResult.getResponse().getContentAsString(), BookingResponse.class);

        mockMvc.perform(post("/bookings/{id}/confirm", reserved.id())).andExpect(status().isOk());

        mockMvc.perform(post("/bookings/{id}/confirm", reserved.id())).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a CANCELLED booking still reports the seats it was made for")
    void cancelledBookingKeepsItsSeatList() throws Exception {
        MvcResult reserveResult = mockMvc.perform(post("/bookings/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ReserveSeatsRequest(1L, java.util.List.of(5L, 6L), "hana@example.com"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.seatNumbers", org.hamcrest.Matchers.containsInAnyOrder("B1", "B2")))
                .andReturn();

        BookingResponse reserved = objectMapper.readValue(reserveResult.getResponse().getContentAsString(), BookingResponse.class);
        mockMvc.perform(post("/bookings/{id}/cancel", reserved.id())).andExpect(status().isOk());

        // The seats are released for reuse, but the booking must not forget what it was for.
        // Reading this off seat.booking_id instead of booking_seat returns [] here, which is
        // the bug booking_seat exists to prevent.
        mockMvc.perform(get("/bookings/{id}", reserved.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.seatNumbers", org.hamcrest.Matchers.containsInAnyOrder("B1", "B2")))
                .andExpect(jsonPath("$.numberOfSeats").value(2))
                .andExpect(jsonPath("$.holdExpiresAt").doesNotExist());

        // ...and the seats really are back in the pool.
        assertThat(seatRepository.findByShowIdAndIdIn(1L, java.util.List.of(5L, 6L)))
                .allMatch(s -> s.getStatus() == com.harshit.backend.bookmyshow.model.SeatStatus.AVAILABLE);

        // The by-email listing must agree with the single-booking read.
        mockMvc.perform(get("/bookings").param("email", "hana@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].seatNumbers", org.hamcrest.Matchers.containsInAnyOrder("B1", "B2")));
    }

    @Test
    @DisplayName("a booking can be re-claimed after its own cancellation released the seats")
    void releasedSeatsCanBeBookedAgain() throws Exception {
        BookingResponse first = objectMapper.readValue(mockMvc.perform(post("/bookings/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ReserveSeatsRequest(1L, java.util.List.of(8L), "first@example.com"))))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString(), BookingResponse.class);

        mockMvc.perform(post("/bookings/{id}/cancel", first.id())).andExpect(status().isOk());

        mockMvc.perform(post("/bookings/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ReserveSeatsRequest(1L, java.util.List.of(8L), "second@example.com"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.seatNumbers", org.hamcrest.Matchers.contains("B4")));
    }

    @Test
    @DisplayName("a CONFIRMED booking cannot be cancelled")
    void confirmedBookingCannotBeCancelled() throws Exception {
        BookingResponse seeded = new BookingResponse(
                1L, "BMSSEED01", 1L, "Inception", "Cineplex A", "Hall 1", "New York",
                null, java.util.List.of("A1", "A2"), 2, new java.math.BigDecimal("600.00"),
                BookingStatus.CONFIRMED, null, "alice@example.com", null);

        mockMvc.perform(post("/bookings/{id}/cancel", seeded.id())).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("bookings can be listed by email")
    void listBookingsByEmail() throws Exception {
        mockMvc.perform(get("/bookings").param("email", "alice@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].bookingNumber").value("BMSSEED01"))
                .andExpect(jsonPath("$[0].seatNumbers", org.hamcrest.Matchers.contains("A1", "A2")));
    }

    @Test
    @DisplayName("seat lookup for an unknown show is a 404")
    void seatsForUnknownShowIsNotFound() throws Exception {
        mockMvc.perform(get("/seats/show/99999")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("shows endpoint returns nothing for a movie with no shows")
    void showsForMovieWithNoShows() throws Exception {
        assertThat(showRepository.existsById(1L)).isTrue();
        mockMvc.perform(get("/shows/movie/99999")).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }
}
