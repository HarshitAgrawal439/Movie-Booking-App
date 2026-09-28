-- ===========================================================================
-- Seed data.
--
-- NOTE THE FILENAME: this file must be lowercase `data.sql`. Spring Boot's
-- default SQL init location is `classpath*:data.sql` and that lookup is
-- case-sensitive once the classes live inside a jar. Naming it `Data.sql`
-- makes the app boot against an empty database in production while looking
-- perfectly fine in the IDE.
--
-- Times are computed relative to CURRENT_DATE so the app has bookable shows
-- "today" whenever it is started. The previous version hard-coded 2023 dates,
-- which meant a search for today returned nothing.
-- ===========================================================================

-- Movies ---------------------------------------------------------------------
INSERT INTO movie (id, title, description, duration, language, release_date, genre) VALUES
    (1, 'Inception', 'A thief who steals corporate secrets through dream-sharing technology.', 148, 'English', '2010-07-16', 'Sci-Fi'),
    (2, 'The Dark Knight', 'Batman faces the Joker, an agent of chaos who pushes Gotham to the brink.', 152, 'English', '2008-07-18', 'Action'),
    (3, 'Interstellar', 'A team of explorers travel through a wormhole in search of a new home for humanity.', 169, 'English', '2014-11-05', 'Sci-Fi');

-- Cinemas --------------------------------------------------------------------
INSERT INTO cinema (id, name, location) VALUES
    (1, 'Cineplex A', 'New York'),
    (2, 'Cineplex B', 'Los Angeles');

-- Cinema halls ----------------------------------------------------------------
INSERT INTO cinema_hall (id, name, cinema_id) VALUES
    (1, 'Hall 1', 1),
    (2, 'Hall 2', 1),
    (3, 'Hall 1', 2);

-- Shows ----------------------------------------------------------------------
-- Two groups, on purpose:
--
--   1,2,3  offset from CURRENT_TIMESTAMP, so they are always in the future whenever
--          the app starts and are therefore always bookable. The previous version
--          used today's fixed 18:00, which quietly made the whole app unbookable
--          after 18:00 local - a bug the tests caught.
--   4,5    at fixed wall-clock times tomorrow, so a date search for today and for
--          tomorrow both return rows.
--
-- Each end time is start + the movie's runtime.
INSERT INTO show (id, start_time, end_time, created_on, movie_id, cinema_hall_id) VALUES
    (1, DATEADD(HOUR, 2, CURRENT_TIMESTAMP),
        DATEADD(MINUTE, 148, DATEADD(HOUR, 2, CURRENT_TIMESTAMP)),
        CURRENT_TIMESTAMP, 1, 1),
    (2, DATEADD(HOUR, 5, CURRENT_TIMESTAMP),
        DATEADD(MINUTE, 148, DATEADD(HOUR, 5, CURRENT_TIMESTAMP)),
        CURRENT_TIMESTAMP, 1, 2),
    (3, DATEADD(HOUR, 8, CURRENT_TIMESTAMP),
        DATEADD(MINUTE, 152, DATEADD(HOUR, 8, CURRENT_TIMESTAMP)),
        CURRENT_TIMESTAMP, 2, 3),
    (4, DATEADD(DAY, 1, DATEADD(HOUR, 18, CAST(CURRENT_DATE AS TIMESTAMP))),
        DATEADD(MINUTE, 169, DATEADD(DAY, 1, DATEADD(HOUR, 18, CAST(CURRENT_DATE AS TIMESTAMP)))),
        CURRENT_TIMESTAMP, 3, 1),
    (5, DATEADD(DAY, 1, DATEADD(HOUR, 20, CAST(CURRENT_DATE AS TIMESTAMP))),
        DATEADD(MINUTE, 152, DATEADD(DAY, 1, DATEADD(HOUR, 20, CAST(CURRENT_DATE AS TIMESTAMP)))),
        CURRENT_TIMESTAMP, 2, 1);

-- Bookings -------------------------------------------------------------------
-- Inserted before seats because seat.booking_id points at this table.
-- One CONFIRMED and one PENDING, so the seeded data exercises every seat state.
INSERT INTO booking (id, booking_number, email, number_of_seats, total_price, status, created_on, updated_on, show_id) VALUES
    (1, 'BMSSEED01', 'alice@example.com', 2, 600.00, 'CONFIRMED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
    (2, 'BMSSEED02', 'bob@example.com',   1, 250.00, 'PENDING',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 3);

-- Seats ----------------------------------------------------------------------
-- Every seat is materialised per show. A1/A2 of show 1 are already sold, so the
-- availability screen has something to render as taken out of the box. The old
-- seed data marked a booking CONFIRMED while leaving all its seats free, which
-- is internally inconsistent and confusing to anyone reading the DB.
INSERT INTO seat (id, seat_no, price, status, hold_expires_at, show_id, booking_id) VALUES
    -- show 1 (Inception, Cineplex A Hall 1) - A1/A2 sold under booking 1
    (1,  'A1', 300.00, 'BOOKED',    null, 1, 1),
    (2,  'A2', 300.00, 'BOOKED',    null, 1, 1),
    (3,  'A3', 300.00, 'AVAILABLE', null, 1, null),
    (4,  'A4', 300.00, 'AVAILABLE', null, 1, null),
    (5,  'B1', 250.00, 'AVAILABLE', null, 1, null),
    (6,  'B2', 250.00, 'AVAILABLE', null, 1, null),
    (7,  'B3', 250.00, 'AVAILABLE', null, 1, null),
    (8,  'B4', 250.00, 'AVAILABLE', null, 1, null),
    -- show 2 (Inception, Cineplex A Hall 2)
    (9,  'A1', 320.00, 'AVAILABLE', null, 2, null),
    (10, 'A2', 320.00, 'AVAILABLE', null, 2, null),
    (11, 'A3', 320.00, 'AVAILABLE', null, 2, null),
    (12, 'A4', 320.00, 'AVAILABLE', null, 2, null),
    (13, 'B1', 280.00, 'AVAILABLE', null, 2, null),
    (14, 'B2', 280.00, 'AVAILABLE', null, 2, null),
    (15, 'B3', 280.00, 'AVAILABLE', null, 2, null),
    (16, 'B4', 280.00, 'AVAILABLE', null, 2, null),
    -- show 3 (Dark Knight, Cineplex B Hall 1) - A1 held under booking 2
    (17, 'A1', 250.00, 'HELD', DATEADD(MINUTE, 5, CURRENT_TIMESTAMP), 3, 2),
    (18, 'A2', 250.00, 'AVAILABLE', null, 3, null),
    (19, 'A3', 250.00, 'AVAILABLE', null, 3, null),
    (20, 'A4', 250.00, 'AVAILABLE', null, 3, null),
    (21, 'B1', 200.00, 'AVAILABLE', null, 3, null),
    (22, 'B2', 200.00, 'AVAILABLE', null, 3, null),
    (23, 'B3', 200.00, 'AVAILABLE', null, 3, null),
    (24, 'B4', 200.00, 'AVAILABLE', null, 3, null),
    -- show 4 (Interstellar, Cineplex A Hall 1) - tomorrow
    (25, 'A1', 300.00, 'AVAILABLE', null, 4, null),
    (26, 'A2', 300.00, 'AVAILABLE', null, 4, null),
    (27, 'A3', 300.00, 'AVAILABLE', null, 4, null),
    (28, 'A4', 300.00, 'AVAILABLE', null, 4, null),
    (29, 'B1', 260.00, 'AVAILABLE', null, 4, null),
    (30, 'B2', 260.00, 'AVAILABLE', null, 4, null),
    (31, 'B3', 260.00, 'AVAILABLE', null, 4, null),
    (32, 'B4', 260.00, 'AVAILABLE', null, 4, null),
    -- show 5 (Dark Knight, Cineplex A Hall 1) - tomorrow
    (33, 'A1', 300.00, 'AVAILABLE', null, 5, null),
    (34, 'A2', 300.00, 'AVAILABLE', null, 5, null),
    (35, 'A3', 300.00, 'AVAILABLE', null, 5, null),
    (36, 'A4', 300.00, 'AVAILABLE', null, 5, null);

-- Identity sequences -------------------------------------------------------
-- Every id above was inserted literally, which does NOT move the GENERATED BY
-- DEFAULT AS IDENTITY counter. Left alone, the first booking created through the
-- API is assigned id 1 and dies on a primary key violation against the seeded
-- row. Restarting each sequence above the seeded range is what makes the seed
-- data and the live application coexist.
ALTER TABLE movie         ALTER COLUMN id RESTART WITH 100;
ALTER TABLE cinema        ALTER COLUMN id RESTART WITH 100;
ALTER TABLE cinema_hall   ALTER COLUMN id RESTART WITH 100;
ALTER TABLE show          ALTER COLUMN id RESTART WITH 100;
ALTER TABLE seat          ALTER COLUMN id RESTART WITH 100;
ALTER TABLE booking       ALTER COLUMN id RESTART WITH 100;
ALTER TABLE booking_seat  ALTER COLUMN id RESTART WITH 100;

-- Booking seat history --------------------------------------------------------
-- Mirrors what reserveSeats() writes at runtime. Without these rows the two seeded
-- bookings would report an empty seat list while still reporting a total, which is
-- exactly the inconsistency this table exists to remove.
INSERT INTO booking_seat (id, booking_id, seat_id, seat_number, price) VALUES
    (1, 1,  1, 'A1', 300.00),
    (2, 1,  2, 'A2', 300.00),
    (3, 2, 17, 'A1', 250.00);
