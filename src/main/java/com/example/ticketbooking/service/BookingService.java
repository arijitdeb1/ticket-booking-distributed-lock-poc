package com.example.ticketbooking.service;

import com.example.ticketbooking.dto.BookingRequest;
import com.example.ticketbooking.dto.BookingResponse;
import com.example.ticketbooking.entity.IdempotencyRecord;
import com.example.ticketbooking.entity.Ticket;
import com.example.ticketbooking.exception.SeatAlreadyBookedException;
import com.example.ticketbooking.lock.DistributedLockService;
import com.example.ticketbooking.repository.IdempotencyRepository;
import com.example.ticketbooking.repository.TicketRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private static final String LOCK_PREFIX = "ticket:lock:";
    private static final long LOCK_LEASE_SECONDS = 10;

    private final TicketRepository ticketRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final DistributedLockService lockService;
    private final ObjectMapper objectMapper;

    @Transactional
    public BookingResponse book(BookingRequest request) {
        // 1. Idempotency check — return cached response for duplicate requestId
        Optional<IdempotencyRecord> existing = idempotencyRepository.findByRequestId(request.getRequestId());
        if (existing.isPresent()) {
            log.info("Duplicate requestId={} — returning cached response", request.getRequestId());
            return deserialize(existing.get().getResponse());
        }

        String lockKey = LOCK_PREFIX + request.getEventId() + ":" + request.getSeatId();

        boolean locked = lockService.tryLock(lockKey, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        if (!locked) {
            log.warn("Could not acquire lock for key={}", lockKey);
            BookingResponse response = BookingResponse.builder()
                    .requestId(request.getRequestId())
                    .eventId(request.getEventId())
                    .seatId(request.getSeatId())
                    .userId(request.getUserId())
                    .status("LOCK_UNAVAILABLE")
                    .message("Another booking for this seat is in progress. Please retry.")
                    .build();
            return response;
        }

        try {
            // 2. Check if seat already booked in DB
            if (ticketRepository.findByEventIdAndSeatId(request.getEventId(), request.getSeatId()).isPresent()) {
                throw new SeatAlreadyBookedException(request.getEventId(), request.getSeatId());
            }

            // 3. Book the ticket
            Ticket ticket = Ticket.builder()
                    .eventId(request.getEventId())
                    .seatId(request.getSeatId())
                    .userId(request.getUserId())
                    .status(Ticket.TicketStatus.BOOKED)
                    .build();
            ticketRepository.save(ticket);

            BookingResponse response = BookingResponse.builder()
                    .requestId(request.getRequestId())
                    .eventId(request.getEventId())
                    .seatId(request.getSeatId())
                    .userId(request.getUserId())
                    .status("BOOKED")
                    .message("Ticket successfully booked.")
                    .build();

            // 4. Persist idempotency record
            idempotencyRepository.save(IdempotencyRecord.builder()
                    .requestId(request.getRequestId())
                    .response(serialize(response))
                    .build());

            log.info("Ticket booked: eventId={}, seatId={}, userId={}", request.getEventId(), request.getSeatId(), request.getUserId());
            return response;

        } catch (SeatAlreadyBookedException e) {
            BookingResponse response = BookingResponse.builder()
                    .requestId(request.getRequestId())
                    .eventId(request.getEventId())
                    .seatId(request.getSeatId())
                    .userId(request.getUserId())
                    .status("ALREADY_BOOKED")
                    .message(e.getMessage())
                    .build();

            idempotencyRepository.save(IdempotencyRecord.builder()
                    .requestId(request.getRequestId())
                    .response(serialize(response))
                    .build());

            return response;
        } finally {
            lockService.unlock(lockKey);
        }
    }

    private String serialize(BookingResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize booking response", e);
        }
    }

    private BookingResponse deserialize(String json) {
        try {
            return objectMapper.readValue(json, BookingResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize booking response", e);
        }
    }
}
