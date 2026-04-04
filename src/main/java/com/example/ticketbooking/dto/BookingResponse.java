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
    private String status;   // PAYMENT_PENDING | BOOKED | ALREADY_BOOKED | LOCK_UNAVAILABLE | PAYMENT_FAILED | NOT_FOUND | VALIDATION_ERROR | ERROR
    private String message;
}
