package com.example.ticketbooking.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PaymentRequest {

    @NotBlank
    private String eventId;

    @NotBlank
    private String seatId;

    @NotBlank
    private String requestId; // same requestId used during booking
}

