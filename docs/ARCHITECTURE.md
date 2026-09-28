# BookMyShow — Architecture and Design

A movie-seat booking backend. The interesting part of this project is not the CRUD; it is
**making sure two people can never end up holding the same seat**, and doing it without paying
throughput for that guarantee.

---

## 1. Scope

| In scope | Out of scope |
| --- | --- |
| Search movies by title / city / date | Authentication, user accounts, roles |
| Browse shows for a movie, seat map for a show | Payment gateway integration (payment is assumed to succeed) |
| Hold seats for a fixed window | Email delivery (a logging listener stands in) |
| Confirm / cancel a booking, view booking history | Multi-tenancy, admin tooling, reporting |

Deliberate omissions are listed again in §11, because knowing what a system *does not* do is
part of the design.

---

## 2. Architecture

Layered, transaction-per-use-case. The dependency arrow only ever points inward.

```
HTTP  ──▶  controller/          thin: validate, delegate, return a DTO
              │
              ▼
          service/             the design lives here
              │                 @Transactional = one business transaction
              ▼
          repository/          Spring Data JPA, one interface per aggregate root
              │
              ▼
          model/               JPA entities + enums
```

Supporting pieces:

| Package | Role |
| --- | --- |
| `dto/` | Records returned by controllers. Entities never leave the service layer. |
| `exception/` | Domain exceptions + `GlobalExceptionHandler` mapping them to status codes. |
| `event/` | `BookingConfirmedEvent`, published **after commit**. |
| `config/` | `Clock` bean, async executor. |
| `service/BookingExpirySweeper` | Scheduled job that releases lapsed holds. |

**Why not hexagonal / clean architecture?** The domain here is small and the interesting
decisions are about *data*, not about ports. A `BookingRepository` interface would sit between
the service and Spring Data with no seam worth testing through. If payment or email integrations
appear, the seam is created at that point rather than pre-emptively.

### Request flow: reserving seats

```
POST /bookings/reserve
  └─▶ BookingService.reserveSeats           @Transactional
        1. load show, reject if it already started
        2. verify every requested seat belongs to this show
        3. snapshot total price from the seat rows
        4. INSERT booking (PENDING)
        5. conditional UPDATE to claim the seats        ◀── the concurrency guarantee
        6. if updated != requested, throw → rollback
        7. INSERT booking_seat rows (permanent history)
        8. return DTO
```

Steps 4–7 share one transaction, so a booking can never exist without its seats, and seats can
never be held without a booking.

---

## 3. Data model

```
Cinema ──1:N──▶ CinemaHall ──1:N──▶ Show ──1:N──▶ Seat
                        ▲              │           │
                        │              │           │ booking_id (current owner, MUTABLE)
                       Movie           │           ▼
                                   Booking ──────┘
                                       │
                                       └──1:N──▶ BookingSeat ──▶ Seat   (history, PERMANENT)
```

`Show` belongs to exactly one `CinemaHall`; a hall plays one movie at a time.

### Why seats are materialised per show

`CinemaHall.totalSeats` and `Cinema.totalCinemaHalls` were removed. The original schema stored
seat counts on the hall, which cannot answer "which seat numbers exist for *this* show, and what
did each cost?" — and pricing varies by show (premium vs. standard). Stating `totalSeats` on the
hall also creates a second source of truth that drifts the moment one seat is booked.

The cost is row growth: a 200-seat hall with 8 shows a day is ~58k seat rows a year per hall.
That is the right trade for a system where seat identity *is* the product.

### The two seat-to-booking links

This is the subtlest decision in the schema, so it is worth stating plainly.

A seat needs to know two different things:

1. **Who owns it right now.** Mutable. Must be cleared the moment a booking is cancelled or
   expires, otherwise the seat can never be re-claimed.
2. **Which booking contained it.** Permanent. Must survive cancellation, because a customer
   reading their booking history needs to see what the booking was for.

A single `seat.booking_id` cannot be both. The first implementation used it for both, and the
symptom was a cancelled booking reporting `seatNumbers: []` alongside `totalPrice: 560.00` — a
self-contradictory record.

| Column | Question it answers | Lifetime | On cancel/expire |
| --- | --- | --- | --- |
| `seat.booking_id` | Who owns this seat now? | mutable | set to `NULL` |
| `booking_seat` rows | What did this booking contain? | permanent | untouched |

`booking_seat` also snapshots `seat_number` and `price`, so a historical booking still reads
correctly if a seat is later repriced or removed. Rows are written once, inside the reservation
transaction, and never updated.

---

## 4. Concurrency: the core of the project

### The problem

Two requests for seat B4 at the same instant. Both read the seat map, both see `AVAILABLE`, both
proceed. Without coordination, two users hold one seat and one of them pays for a seat that is
no longer theirs.

### The solution: a conditional UPDATE

```java
@Modifying
@Query("""
        update Seat s
           set s.status = :heldStatus,
               s.holdExpiresAt = :holdExpiresAt,
               s.booking = :booking
         where s.show.id = :showId
           and s.id in :seatIds
           and (s.status = :availableStatus
                or (s.status = :heldStatus and s.holdExpiresAt <= :now))
        """)
int claimSeats(...);
```

The database evaluates the precondition and performs the write as **one indivisible statement**.
Both transactions run the same statement; the first to execute changes the row so the second
matches zero rows and loses. No locks are taken, no read-then-write window exists, and the
loser's booking insert is rolled back with the rest of its transaction.

The caller checks the row count against the requested count:

```java
if (claimed != seatIds.size()) {
    throw new SeatsUnavailableException(...);   // rollback undoes the booking row too
}
```

An all-or-nothing request matters: a user asking for 4 seats where only 3 are free must get
**nothing**, not a surprise partial booking.

### Why not `Isolation.SERIALIZABLE`?

The reflexive answer to a booking race is to escalate isolation and catch
`CannotSerializeTransactionException`. It is worse here:

- it converts contention into **aborted transactions**, so callers must retry — retry storms
  under exactly the load the system exists to handle;
- it forces range/predicate locks on the seat scan, so unrelated shows contend with each other;
- it solves a broader problem than exists, because a single conditional `UPDATE` already closes
  the window.

`READ_COMMITTED` plus a conditional write is both sufficient and cheaper.

### Why the FK is on `seat`, not the other way round

`seat.booking_id` exists so the claim can be expressed as a conditional `UPDATE` on the seat
row. A join table cannot express "claim these seats if they are free" atomically without a
`SELECT ... FOR UPDATE` loop or an isolation escalation. Ownership is high-churn and belongs on
the contended row; history is immutable and belongs in its own table.

### State transitions are compare-and-set too

The seat claim is not the only race. Confirm, cancel, and the expiry sweeper all move a booking
out of `PENDING`, and an in-memory check is worthless:

```java
if (booking.getStatus() != BookingStatus.PENDING) { ... }   // three threads all pass this
```

So the transition itself is conditional:

```java
@Query("update Booking b set b.status = :newStatus, b.updatedOn = :now "
     + "where b.id = :id and b.status = :expectedStatus")
int transitionStatus(...);
```

Returning `0` means someone else moved the booking first. Exactly one of confirm / cancel /
sweep can win, and the losers are told so with `409`.

### Hold expiry is a read-time rule, not a job

Availability is decided by comparing `hold_expires_at` to `now` **inside the claim statement**,
so correctness never depends on the sweeper having run. The sweeper is housekeeping: it stops
stored status from *lying* about holds that have already lapsed. Deleting the scheduler would
make the system less tidy, not less correct.

`confirmSeats` carries `and s.holdExpiresAt > :now`, so a user who pays two seconds late is
refused even if the sweeper has not run yet. The service then checks
`confirmed == numberOfSeats` rather than `> 0`, so a booking can never be marked `CONFIRMED`
with only some of its seats booked.

### Verified, not asserted

`SeatConcurrencyTest` fires 8 requests at one seat through a `CyclicBarrier` and asserts exactly
one winner, and separately fires two overlapping seat sets and asserts at most one can win. It is
deliberately **not** `@Transactional` — a test-managed transaction would put every worker in one
persistence context and hide the race entirely.

Loser exceptions are collected and asserted on (`SeatsUnavailableException` or a write conflict),
not swallowed: a test that accepts any exception passes just as happily when the endpoint is
broken for an unrelated reason.

---

## 5. Transactions

`@Transactional` sits on the **service**, one per use case. Controllers and repositories hold none
of their own, so a reservation is a single transaction no matter how many internal steps it takes.

| Operation | Atomic unit |
| --- | --- |
| reserve | booking insert + seat claim + `booking_seat` insert |
| confirm | status transition + seat promotion |
| cancel | status transition + hold release |
| search / read | read-only, no lock escalation |

`@Transactional(propagation = NOT_SUPPORTED)` is used by the concurrency test only.

---

## 6. API design

| Method | Path | Success | Notes |
| --- | --- | --- | --- |
| GET | `/movies/search` | 200 | `title`, `city`, `date`; paged |
| GET | `/movies/by-title` | 200 | exact title |
| GET | `/shows/movie/{id}` | 200 | shows with cinema + hall |
| GET | `/seats/show/{id}` | 200 | seat map with live status |
| POST | `/bookings/reserve` | **202** | seats are *held*, not owned |
| POST | `/bookings/{id}/confirm` | 200 | payment succeeded |
| POST | `/bookings/{id}/cancel` | 200 | releases the hold |
| GET | `/bookings/{id}` | 200 | |
| GET | `/bookings?email=` | 200 | booking history |

`202 Accepted` for reserve is intentional: the request succeeded, but the booking is provisional
and will lapse unless confirmed. `200` would imply the booking is final.

### Error mapping

| Status | Cause |
| --- | --- |
| 400 | malformed body, bad email, seat not in this show, show already started |
| 404 | unknown show / movie / booking / endpoint |
| 409 | seat conflict, booking not in a valid state, concurrent modification |
| 500 | genuinely unexpected |

Handled explicitly by `GlobalExceptionHandler`, including `NoResourceFoundException` → 404 so a
malformed URL does not get reported as a server fault and logged with a stack trace.

---

## 7. Notable implementation details

### `open-in-view=false`

The default since Boot 2.2, set explicitly because the whole service layer depends on it: DTO
mapping happens inside the transaction, so a lazy collection is never touched after the session
closes. `BookingResponse.from` takes pre-fetched `booking_seat` rows for exactly this reason.

### `Clock` is injected

Every time-dependent rule reads `LocalDateTime.now(clock)`. That is what makes hold expiry
testable without `Thread.sleep`: `BookingHoldExpiryTest` replaces the `Clock` bean with a movable
one and advances it. A time rule that can only be tested by sleeping is a rule that is never
tested.

### `data.sql` must be lowercase

Spring Boot resolves the default location `classpath*:data.sql`, and classpath lookups inside a
packaged jar are case-sensitive. A file named `Data.sql` loads fine from an IDE and is **silently
skipped in the fat jar**, because the location is prefixed with `optional:`. The result is an app
that starts cleanly against an empty database.

### Identity sequences must be restarted after seeding

`data.sql` inserts literal primary keys, which does **not** advance the
`GENERATED BY DEFAULT AS IDENTITY` counter. Without `ALTER TABLE ... ALTER COLUMN id RESTART WITH 100`,
the first booking created through the API is assigned id 1 and dies on a primary key violation
against a seeded row. The tests caught this.

### Seeded shows are relative to `CURRENT_TIMESTAMP`

Shows are seeded at `CURRENT_TIMESTAMP + 2h/5h/8h` (plus two at fixed times tomorrow for date
search). The original seed used *today at 18:00*, which silently made the entire application
unbookable after 18:00 local — the API correctly refused with "show has already started", and
the failure looked like a product bug rather than a seed bug.

### Lombok on JDK 25

Lombok 1.18.34 (the version Spring Boot 3.3.2 inherits) fails on JDK 25. Fixed with Lombok
1.18.42 plus `<proc>full</proc>` on `maven-compiler-plugin`, since JDK 23+ no longer implicitly
discovers annotation processors on the classpath.

---

## 8. Testing

27 tests, all green, no sleeps.

| Suite | Covers |
| --- | --- |
| `BookingFlowIntegrationTest` (18) | full HTTP journey, error mapping, validation, cancelled-booking history |
| `BookingHoldExpiryTest` (5) | hold window via a movable clock, sweeper, late payment |
| `SeatConcurrencyTest` (3) | 8-way seat race, overlapping sets, no mutation on invalid input |
| `BookMyShowApplicationTests` (1) | context loads |

`AbstractBookingTest` restores the exact `data.sql` state before each test. Spring caches one
context per configuration and H2 (`DB_CLOSE_DELAY=-1`) lives for the whole JVM, so without this
reset a booking made by one test is still present for the next and the suite fails in an
order-dependent way that looks like a product bug.

---

## 9. Performance notes

- `EXISTS`-style claims avoid row locking except on the seats actually requested.
- The seat-map read is a single indexed query per show.
- Booking history is loaded in **one** query for the whole page, not one per booking.
- `uk_booking_seat (booking_id, seat_id)` prevents duplicate history rows.
- Search is paged; indexes on `movie.title`, `cinema.location`, `show.start_time`.

The obvious optimisation, replacing the conditional `UPDATE` with an in-memory check plus an
optimistic-lock retry, was rejected: it converts a rare, self-correcting conflict into a retry
storm, and makes correctness depend on the retry loop rather than on the database.

---

## 10. Production gaps

Honest list of what is not production-ready:

1. **H2 in-memory.** Data is lost on restart. Move to Postgres and set `ddl-auto=validate`.
2. **No authentication.** Anyone with a booking id can confirm, cancel, or read it; anyone can
   list bookings by email. This is the most serious gap.
3. **No payment integration.** `/confirm` trusts the caller. Real money flow needs a webhook plus
   signature verification and an idempotency key, so a retried payment webhook cannot double-apply.
4. **Notification is a log statement.** No outbox table, so a crash between commit and send loses
   the email. A transactional outbox is the fix.
5. **In-process async executor.** Notifications are lost if the JVM dies. See the outbox above.
6. **No seat-map caching.** Fine at this scale; a popular show's seat map is the hot read.
7. **Single-node scheduler.** Multiple instances each run the sweeper. Harmless here (the
   operations are idempotent) but it is wasted work and would need a leader lock at scale.
8. **No double-booking prevention for overlapping shows in one hall.** Scheduling is trusted.
9. **No rate limiting** on reserve, the endpoint most worth abusing.

---

## 11. Design decisions, summarised

| Decision | Alternative | Why this one |
| --- | --- | --- |
| Conditional `UPDATE` to claim | `SERIALIZABLE` + retry | No aborted transactions, no retry storm, no cross-show contention |
| Mutable ownership on `seat`, history in `booking_seat` | One `booking_id` | One column cannot be both mutable and permanent |
| Seats materialised per show | Seat counts on the hall | Seat identity and per-show pricing are the product |
| `202` on reserve | `200` | The booking is provisional until confirmed |
| `Clock` injected | `LocalDateTime.now()` | Expiry is testable without sleeping |
| `READ_COMMITTED` | `SERIALIZABLE` | The atomic write already closes the window |
| Explicit exception mapping | Default Spring errors | A 409 tells clients not to retry; a 500 makes them retry a doomed request |
| Lowercase `data.sql` + sequence restarts | — | Fat-jar case sensitivity and identity counters are real traps |
