package com.example.ticketbooking.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BookingResponse {

    private String requestId;
    private String eventId;
    private String seatId;
    private String userId;
    private String status;   // BOOKED | ALREADY_BOOKED | DUPLICATE_REQUEST
    private String message;
}
