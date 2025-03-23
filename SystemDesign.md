# Backend Project BookMyShow
## Functionality
    - Search for a movie with certain params
        - get list of available movies

    - Select a movie
        - get list of available shows

    - Select a show
        - get list of available seats (non reserved)

    - Select the number of seats to reserve

    - Booking *(success or failure)

## Ensure
    - No race condition (two users reserve same seat)
    - cases of failures 
        - Show can be housefull
        - Other users are at payment page
    - Send email when movie booked

## System Api's
    - SearchMovie (movieName, city, date) -> list of shows
    - getShowDetails (showId) -> list of seats
    - reserveSeats(showId, List Of seatIds) -> status of reservation
        - Hold seat for 5 minutes till payment success and remove from available seats. If it fails revert back.

## Database Design
    - Cinema (multiple halls) - H1....Hn
    - Each movie have multiple shows - Timings
    - Users can search by title , city and date

    > Entities
        - Movie -> title, desc, duration, language, releaseDate, genre
        - Cinema -> name, totalCinemaHalls, location
        - show -> createdOn, startTime, endTime
        - cinemahall -> name, totalSeats

    > Tables
        - show seat -> seatNo, Price, isReserved, showId
        - Booking -> bookingNumber, numberOfSeats, createdOn, status, showId

## Packages Structure
    - Controller Packages
    - Service Pakcages
    - DAO class


## Dealing real world problems
    - Concurrency issues
        - app level or database level *(transaction isolation level)
        