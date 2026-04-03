package com.example.ticketbooking.controller;

import com.example.ticketbooking.dto.BookingRequest;
import com.example.ticketbooking.dto.BookingResponse;
import com.example.ticketbooking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    /**
     * POST /api/book
     * Body: { "eventId": "E1", "seatId": "S1", "userId": "U1", "requestId": "<UUID>" }
     */
    @PostMapping("/book")
    public ResponseEntity<BookingResponse> book(@Valid @RequestBody BookingRequest request) {
        BookingResponse response = bookingService.book(request);
        return ResponseEntity.ok(response);
    }
}
