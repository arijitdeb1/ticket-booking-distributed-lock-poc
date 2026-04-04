package com.example.ticketbooking.controller;

import com.example.ticketbooking.dto.BookingResponse;
import com.example.ticketbooking.dto.PaymentRequest;
import com.example.ticketbooking.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * POST /api/payment
     * Body: { "eventId": "E1", "seatId": "S1", "requestId": "<UUID>" }
     *
     * Simulates payment processing with lock lease renewal.
     * Call this after POST /api/book returns PAYMENT_PENDING.
     */
    @PostMapping("/payment")
    public ResponseEntity<BookingResponse> payment(@Valid @RequestBody PaymentRequest request) {
        BookingResponse response = paymentService.processPayment(request);
        return ResponseEntity.ok(response);
    }
}

