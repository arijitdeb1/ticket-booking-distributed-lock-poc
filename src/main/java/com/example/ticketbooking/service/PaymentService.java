package com.example.ticketbooking.service;

import com.example.ticketbooking.dto.BookingResponse;
import com.example.ticketbooking.dto.PaymentRequest;
import com.example.ticketbooking.entity.Ticket;
import com.example.ticketbooking.entity.IdempotencyRecord;
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
public class PaymentService {

    private static final String LOCK_PREFIX = "ticket:lock:";
    private static final long LOCK_LEASE_SECONDS = 30;
    private static final long PAYMENT_SLEEP_MS = 40_000;

    private final TicketRepository ticketRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final DistributedLockService lockService;
    private final ObjectMapper objectMapper;

    @Transactional
    public BookingResponse processPayment(PaymentRequest request) {

        // 1. Look up ticket by eventId + seatId
        Optional<Ticket> ticketOpt = ticketRepository.findByEventIdAndSeatId(
                request.getEventId(), request.getSeatId());

        if (ticketOpt.isEmpty()) {
            log.warn("No ticket found for eventId={}, seatId={}", request.getEventId(), request.getSeatId());
            return buildResponse(request, null, "NOT_FOUND",
                    "No booking found. Please call POST /api/book first.");
        }

        Ticket ticket = ticketOpt.get();

        if (ticket.getStatus() == Ticket.TicketStatus.BOOKED) {
            log.info("Ticket already BOOKED for eventId={}, seatId={}", request.getEventId(), request.getSeatId());
            return buildResponse(request, ticket.getUserId(), "ALREADY_BOOKED",
                    "Payment already completed for this seat.");
        }

        if (ticket.getStatus() != Ticket.TicketStatus.PAYMENT_PENDING) {
            log.warn("Ticket not in PAYMENT_PENDING state for eventId={}, seatId={}", request.getEventId(), request.getSeatId());
            return buildResponse(request, ticket.getUserId(), "ERROR",
                    "Ticket is not awaiting payment. Current status: " + ticket.getStatus());
        }

        // 2. Acquire lock for payment processing
        String lockKey = LOCK_PREFIX + request.getEventId() + ":" + request.getSeatId();
        boolean locked = lockService.tryLock(lockKey, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);

        if (!locked) {
            log.warn("Could not acquire lock for payment key={}", lockKey);
            return buildResponse(request, ticket.getUserId(), "LOCK_UNAVAILABLE",
                    "Another payment for this seat is in progress. Please retry.");
        }

        try {
            // 3. Simulate payment with Thread.sleep + conditional lease renewal
            long sleepTimeMs = PAYMENT_SLEEP_MS;
            long leaseTimeMs = LOCK_LEASE_SECONDS * 1_000;

            if (sleepTimeMs > leaseTimeMs) {
                // Sleep until 2 seconds before the lock expires
                long firstSleepMs = leaseTimeMs - 2_000;
                log.info("Payment started — sleeping {}ms (lease={}ms)", sleepTimeMs, leaseTimeMs);
                Thread.sleep(firstSleepMs);

                // Extend the lease using reentrant lock.lock() before it expires
                log.info("Payment exceeds lease — renewing lock for key={}", lockKey);
                lockService.renewLease(lockKey, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);

                // Sleep the remaining time under the renewed lease
                long remainingMs = sleepTimeMs - firstSleepMs;
                log.info("Continuing payment — sleeping remaining {}ms", remainingMs);
                Thread.sleep(remainingMs);
            } else {
                log.info("Payment — sleeping {}ms (within lease)", sleepTimeMs);
                Thread.sleep(sleepTimeMs);
            }

            log.info("Payment simulation completed for eventId={}, seatId={}",
                    request.getEventId(), request.getSeatId());

            // 4. Update ticket status to BOOKED
            ticket.setStatus(Ticket.TicketStatus.BOOKED);
            ticketRepository.save(ticket);

            BookingResponse response = buildResponse(request, ticket.getUserId(), "BOOKED",
                    "Payment successful. Ticket is now booked.");

            // 5. Update idempotency record with final BOOKED response
            idempotencyRepository.save(IdempotencyRecord.builder()
                    .requestId(request.getRequestId())
                    .response(serialize(response))
                    .build());

            log.info("Ticket BOOKED after payment: eventId={}, seatId={}",
                    request.getEventId(), request.getSeatId());
            return response;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Payment interrupted for eventId={}, seatId={}",
                    request.getEventId(), request.getSeatId());

            BookingResponse response = buildResponse(request, ticket.getUserId(), "PAYMENT_FAILED",
                    "Payment was interrupted.");
            return response;

        } finally {
            lockService.unlock(lockKey);
        }
    }

    private BookingResponse buildResponse(PaymentRequest request, String userId, String status, String message) {
        return BookingResponse.builder()
                .requestId(request.getRequestId())
                .eventId(request.getEventId())
                .seatId(request.getSeatId())
                .userId(userId)
                .status(status)
                .message(message)
                .build();
    }

    private String serialize(BookingResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize payment response", e);
        }
    }
}

