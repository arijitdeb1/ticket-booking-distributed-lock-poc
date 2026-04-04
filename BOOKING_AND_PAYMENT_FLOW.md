# Booking & Payment Flow

## Overview

The ticket booking system is split into two API calls — **booking** (`POST /api/book`) and **payment** (`POST /api/payment`). The booking step reserves a seat under a distributed lock and leaves it in `PAYMENT_PENDING` state. The payment step acquires its own lock, simulates a long-running payment gateway call using `Thread.sleep`, extends the lock lease when the processing time exceeds the original TTL, and transitions the ticket to `BOOKED`.

Both steps use **idempotency**, **distributed locking**, and a **database unique constraint** to guarantee exactly-once booking per seat under concurrent traffic.

---

# Part 1 — Booking Flow (`BookingService.book()`)

---

## Step 1 — Idempotency Check

The method looks up the incoming `requestId` in the `idempotency` table. If the same request was already processed, the cached JSON response is deserialized and returned immediately:

```java
Optional<IdempotencyRecord> existing = idempotencyRepository.findByRequestId(request.getRequestId());
if (existing.isPresent()) {
    return deserialize(existing.get().getResponse());
}
```

This makes retries safe — a client can re-send the same request and always get back the original outcome.

---

## Step 2 — Distributed Lock Acquisition

The method constructs a Redis lock key scoped to the event and seat (`ticket:lock:{eventId}:{seatId}`) and attempts to acquire it with **zero wait time** and a **30-second lease**. Zero wait time means fail-fast — if another thread already holds the lock, the method returns `LOCK_UNAVAILABLE` immediately:

```java
boolean locked = lockService.tryLock(lockKey, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
if (!locked) {
    // return LOCK_UNAVAILABLE response
}
```

The 30-second lease is a safety net — if the application crashes mid-booking, Redis auto-releases the lock after expiry, preventing deadlocks.

---

## Step 3 — Seat Availability Check

Once the lock is held, the method queries the database for an existing ticket with the same `(eventId, seatId)`. If one already exists, it throws `SeatAlreadyBookedException`:

```java
if (ticketRepository.findByEventIdAndSeatId(request.getEventId(), request.getSeatId()).isPresent()) {
    throw new SeatAlreadyBookedException(request.getEventId(), request.getSeatId());
}
```

---

## Step 4 — Save Ticket as PAYMENT_PENDING

If the seat is available, the method persists a `Ticket` with status `PAYMENT_PENDING` and caches the response in the idempotency table. The ticket is **not yet booked** — payment must be completed via a separate API call:

```java
Ticket ticket = Ticket.builder()
        .eventId(request.getEventId())
        .seatId(request.getSeatId())
        .userId(request.getUserId())
        .status(Ticket.TicketStatus.PAYMENT_PENDING)
        .build();
ticketRepository.save(ticket);
```

The response tells the client to proceed to the payment endpoint:

```json
{
  "status": "PAYMENT_PENDING",
  "message": "Seat reserved. Proceed to POST /api/payment to complete booking."
}
```

---

## Step 5 — Already-Booked Handling

The `catch` block handles `SeatAlreadyBookedException` gracefully. It builds an `ALREADY_BOOKED` response **and persists it** to the idempotency table, so retries of that `requestId` consistently get the same outcome:

```java
catch (SeatAlreadyBookedException e) {
    BookingResponse response = BookingResponse.builder()
            .status("ALREADY_BOOKED")
            .message(e.getMessage())
            .build();
    idempotencyRepository.save(...);
    return response;
}
```

---

## Step 6 — Lock Release

Regardless of success or failure, the `finally` block ensures the distributed lock is always released:

```java
finally {
    lockService.unlock(lockKey);
}
```

---

# Part 2 — Payment Flow (`PaymentService.processPayment()`)

---

## Step 1 — Ticket Lookup and State Validation

The payment service looks up the ticket by `(eventId, seatId)` and validates its state before proceeding:

```java
Optional<Ticket> ticketOpt = ticketRepository.findByEventIdAndSeatId(
        request.getEventId(), request.getSeatId());
```

| Ticket state | Result |
|---|---|
| Not found | `NOT_FOUND` — book first |
| Already `BOOKED` | `ALREADY_BOOKED` — payment already completed |
| Not `PAYMENT_PENDING` | `ERROR` — unexpected state |
| `PAYMENT_PENDING` | Proceed to payment |

---

## Step 2 — Lock Acquisition for Payment

Same lock key format as booking (`ticket:lock:{eventId}:{seatId}`), same fail-fast behavior:

```java
String lockKey = LOCK_PREFIX + request.getEventId() + ":" + request.getSeatId();
boolean locked = lockService.tryLock(lockKey, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
```

If the lock is unavailable, the response is `LOCK_UNAVAILABLE`.

---

## Step 3 — Payment Simulation with Lease Renewal

This is the core of the payment flow. A `Thread.sleep(40000)` simulates a payment gateway call that takes **40 seconds** — intentionally longer than the **30-second** lock lease.

**When sleep time exceeds lease time**, the service splits the sleep into two parts:

```java
if (sleepTimeMs > leaseTimeMs) {
    long firstSleepMs = leaseTimeMs - 2_000;       // sleep 28s (wake 2s before expiry)
    Thread.sleep(firstSleepMs);

    lockService.renewLease(lockKey, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);  // extend +30s

    long remainingMs = sleepTimeMs - firstSleepMs;  // sleep remaining 12s
    Thread.sleep(remainingMs);
}
```

**When sleep time fits within lease time**, it sleeps normally in one shot:

```java
else {
    Thread.sleep(sleepTimeMs);
}
```

The `renewLease` method uses **reentrant `RLock.lock(leaseTime, unit)`** — calling `lock()` on an already-held lock resets the Redis key TTL to a fresh lease from now.

### Timeline

```
0s              28s                        40s
|--- sleep ------|-- renewLease (fresh 30s) --|--- sleep remaining 12s ---|
                 ↑ new 30s TTL starts here     ↑ payment completes
                   (total coverage = 58s)
```

---

## Step 4 — Update Ticket to BOOKED

After the simulated payment completes, the ticket status is updated from `PAYMENT_PENDING` to `BOOKED`:

```java
ticket.setStatus(Ticket.TicketStatus.BOOKED);
ticketRepository.save(ticket);
```

The idempotency record is also updated with the final `BOOKED` response so retries return the correct outcome.

---

## Step 5 — Interruption Handling

If the thread is interrupted during the sleep, the payment is considered failed:

```java
catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    // return PAYMENT_FAILED response
}
```

---

## Step 6 — Lock Release

Same as the booking flow — the `finally` block always releases the lock:

```java
finally {
    lockService.unlock(lockKey);
}
```

---

# Response Status Summary

| Scenario | API | `status` |
|---|---|---|
| Seat reserved, awaiting payment | `POST /api/book` | `PAYMENT_PENDING` |
| Seat already taken | `POST /api/book` | `ALREADY_BOOKED` |
| Same `requestId` retry (booking) | `POST /api/book` | Cached original response |
| Lock held by another request | Both | `LOCK_UNAVAILABLE` |
| No booking found for payment | `POST /api/payment` | `NOT_FOUND` |
| Payment already completed | `POST /api/payment` | `ALREADY_BOOKED` |
| Payment succeeded | `POST /api/payment` | `BOOKED` |
| Payment interrupted | `POST /api/payment` | `PAYMENT_FAILED` |
| Validation error | Both | `VALIDATION_ERROR` |
| Unexpected error | Both | `ERROR` |

---

# Design Notes

- **`LOCK_UNAVAILABLE` is not persisted** to the idempotency table. This lets the client retry the same `requestId` once contention clears.
- **Lease renewal uses reentrant locking** — `RLock.lock(leaseTime, unit)` on an already-held lock resets the TTL without releasing and re-acquiring. This is safe because Redisson tracks the owner thread internally.
- **`RLock` does not expose a synchronous `expire()` method** in Redisson 3.27.2. The reentrant `lock()` call is the recommended way to extend a lease at the `RLock` interface level.
