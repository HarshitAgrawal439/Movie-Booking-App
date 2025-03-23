-- Insert Movies
INSERT INTO Movie (id, title, description, duration, language, release_date, genre) VALUES
                                                                                        (1, 'Inception', 'A thief who steals corporate secrets through the use of dream-sharing technology.', 148, 'English', '2010-07-16', 'Sci-Fi'),
                                                                                        (2, 'The Dark Knight', 'When the menace known as The Joker emerges from his mysterious past.', 152, 'English', '2008-07-18', 'Action');

-- Insert Cinemas
INSERT INTO Cinema (id, name, total_cinema_halls, location) VALUES
                                                                (1, 'Cineplex A', 3, 'New York'),
                                                                (2, 'Cineplex B', 2, 'Los Angeles');

-- Insert CinemaHalls
INSERT INTO Cinema_Hall (id, name, total_seats, cinema_id) VALUES
                                                              (1, 'Hall 1', 100, 1),
                                                              (2, 'Hall 2', 150, 1),
                                                              (3, 'Hall 3', 120, 1),
                                                              (4, 'Hall 1', 200, 2),
                                                              (5, 'Hall 2', 180, 2);

-- Insert Shows
INSERT INTO Show (id, created_on, start_time, end_time, movie_id, cinema_hall_id) VALUES
                                                                                      (1, '2023-07-01 10:00:00', '2023-07-02 18:00:00', '2023-07-02 20:30:00', 1, 1),
                                                                                      (2, '2023-07-01 10:00:00', '2023-07-02 21:00:00', '2023-07-02 23:30:00', 2, 2),
                                                                                      (3, '2023-07-01 10:00:00', '2023-07-03 18:00:00', '2023-07-03 20:30:00', 1, 3),
                                                                                      (4, '2023-07-01 10:00:00', '2023-07-03 21:00:00', '2023-07-03 23:30:00', 2, 4);

-- Insert Seats
INSERT INTO Seat (id, seat_no, price, is_reserved, show_id) VALUES
                                                                (1, 'A1', 10.00, false, 1),
                                                                (2, 'A2', 10.00, false, 1),
                                                                (3, 'A3', 10.00, false, 1),
                                                                (4, 'A4', 10.00, false, 1),
                                                                (5, 'B1', 10.00, false, 2),
                                                                (6, 'B2', 10.00, false, 2),
                                                                (7, 'B3', 10.00, false, 2),
                                                                (8, 'B4', 10.00, false, 2),
                                                                (9, 'C1', 10.00, false, 3),
                                                                (10, 'C2', 10.00, false, 3),
                                                                (11, 'C3', 10.00, false, 3),
                                                                (12, 'C4', 10.00, false, 3),
                                                                (13, 'D1', 10.00, false, 4),
                                                                (14, 'D2', 10.00, false, 4),
                                                                (15, 'D3', 10.00, false, 4),
                                                                (16, 'D4', 10.00, false, 4);

-- Insert Bookings
INSERT INTO Booking (id, booking_number, number_of_seats, created_on, status, show_id) VALUES
                                                                                           (1, 'BKG12345', 2, '2023-07-01 11:00:00', 'CONFIRMED', 1),
                                                                                           (2, 'BKG12346', 4, '2023-07-01 11:05:00', 'PENDING', 2);
