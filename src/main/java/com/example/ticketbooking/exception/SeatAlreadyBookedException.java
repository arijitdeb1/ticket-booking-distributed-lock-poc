package com.example.ticketbooking.exception;

public class SeatAlreadyBookedException extends RuntimeException {

    public SeatAlreadyBookedException(String eventId, String seatId) {
        super("Seat " + seatId + " for event " + eventId + " is already booked.");
    }
}
