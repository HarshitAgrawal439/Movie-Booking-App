# Backend Project BookMyShow

A movie-seat booking backend built with Spring Boot 3, Spring Data JPA and H2.

**The full design write-up lives in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)** — data model,
the concurrency approach, transactions, API contract, and an honest list of production gaps.

## Functionality

- Search movies by title, city and date
- Browse shows for a movie, and the seat map for a show
- Hold seats for 5 minutes while the user is on the payment page
- Confirm (payment succeeded) or cancel a booking
- View booking history by email
- Log a confirmation notification once the booking commits

## Guarantees

- **No double booking.** Two concurrent requests for the same seat cannot both win. Enforced by a
  conditional `UPDATE` rather than an isolation-level escalation — see
  [ARCHITECTURE.md §4](docs/ARCHITECTURE.md).
- **All-or-nothing holds.** A request for 4 seats where 3 are free gets nothing, not a partial booking.
- **Holds expire.** Availability is decided by comparing `hold_expires_at` to now at claim time, so
  correctness does not depend on the cleanup job having run.
- **No double charge.** Confirming twice is a `409`, not a second success.
- **Honest history.** A cancelled booking still reports the seats it was made for.

## System APIs

| Method | Path | Returns |
| --- | --- | --- |
| GET | `/movies/search` | paged movies matching title / city / date |
| GET | `/movies/by-title` | movie by exact title |
| GET | `/shows/movie/{movieId}` | shows with cinema and hall |
| GET | `/seats/show/{showId}` | seat map with live availability |
| POST | `/bookings/reserve` | `202` + booking, seats held for 5 minutes |
| POST | `/bookings/{id}/confirm` | confirmed booking |
| POST | `/bookings/{id}/cancel` | cancelled booking, seats released |
| GET | `/bookings/{id}` | one booking |
| GET | `/bookings?email=` | booking history |

## Database design

```
Cinema ──1:N──▶ CinemaHall ──1:N──▶ Show ──1:N──▶ Seat
                        ▲              │           │
                        │              │           │ booking_id (current owner)
                       Movie           │           ▼
                                   Booking ──────┘
                                       │
                                       └──1:N──▶ BookingSeat ──▶ Seat  (permanent history)
```

- **Movie** — title, description, duration, language, releaseDate, genre
- **Cinema** — name, location
- **CinemaHall** — name, cinema
- **Show** — startTime, endTime, movie, cinemaHall
- **Seat** — seatNo, price, status, holdExpiresAt, show, booking *(current owner)*
- **Booking** — bookingNumber, numberOfSeats, totalPrice, status, show, email
- **BookingSeat** — booking, seat, seatNumber, price *(permanent record of what the booking held)*

Seats are materialised **per show** rather than counted on the hall, because seat identity and
per-show pricing are the product. Seat counts on the hall cannot answer "which seat numbers exist
for this show, and what does each cost?"

The split between `seat.booking_id` and `booking_seat` exists because one column cannot be both
*mutable* (so seats can be released) and *permanent* (so booking history survives cancellation).

## Package structure

```
controller/   thin HTTP layer: validate, delegate, return a DTO
service/      business logic + transaction boundary
repository/   Spring Data JPA, one per aggregate root
model/        JPA entities and enums
dto/          records returned to clients
exception/    domain exceptions and the HTTP status mapping
event/        BookingConfirmedEvent, published after commit
config/       Clock bean, async executor
```

## Running it

```bash
cd BookMyShow
./mvnw clean package
java -jar target/BookMyShow-0.0.1-SNAPSHOT.jar
```

Then, for example:

```bash
curl 'http://localhost:8080/movies/search?city=New%20York&date='"$(date +%F)"
curl 'http://localhost:8080/shows/movie/1'
curl 'http://localhost:8080/seats/show/1'
curl -X POST http://localhost:8080/bookings/reserve \
  -H 'Content-Type: application/json' \
  -d '{"showId":1,"seatIds":[3,4],"email":"you@example.com"}'
```

Seeded shows start 2, 5 and 8 hours after startup, so the app is always bookable when you run it.

## Tests

```bash
cd BookMyShow
./mvnw test
```

27 tests, no `Thread.sleep` anywhere. Covers the full HTTP flow, hold expiry via an injectable
`Clock`, and an 8-thread race for a single seat.
