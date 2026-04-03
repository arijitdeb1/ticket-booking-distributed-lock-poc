package com.example.ticketbooking.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BookingRequest {

    @NotBlank
    private String eventId;

    @NotBlank
    private String seatId;

    @NotBlank
    private String userId;

    @NotBlank
    private String requestId; // UUID for idempotency
}
