# Ticket Booking POC using Redisson & Redlock

## Suggested GitHub Repo Names
- ticket-booking-distributed-lock-poc
- redisson-redlock-ticketing-demo
- distributed-lock-ticketmaster-poc
- ticket-booking-idempotency-lock-demo

---

## Objective
Demonstrate how to prevent duplicate ticket booking using:
1. Redisson (single Redis instance)
2. Redisson Redlock (multi Redis nodes)

---

## Tech Stack
- Spring Boot
- Redisson
- Redis (Docker)
- H2 / PostgreSQL
- REST APIs

---

## Use Case
User tries to book same ticket multiple times concurrently → prevent duplicate booking.

---

## System Design

### Key Concepts
- Lock Key: ticket:lock:{eventId}:{seatId}
- Idempotency Key: requestId (UUID)

---

## Variant 1: Redisson (Single Redis)

### Flow
1. Acquire lock using Redisson RLock
2. Check DB if ticket already booked
3. If not → book ticket
4. Release lock

### Code Snippet
```java
RLock lock = redissonClient.getLock("ticket:lock:" + seatId);
lock.lock();
try {
    // check + book
} finally {
    lock.unlock();
}
```

---

## Variant 2: Redlock (Multi Redis)

### Setup
- 3 Redis containers
- 3 Redisson clients

### Flow
1. Acquire locks across nodes
2. Check quorum success
3. Proceed with booking
4. Release locks

### Code Snippet
```java
RedissonRedLock lock = new RedissonRedLock(lock1, lock2, lock3);
lock.lock();
try {
    // booking logic
} finally {
    lock.unlock();
}
```

---

## API Design

### POST /book
Request:
{
  "eventId": "E1",
  "seatId": "S1",
  "userId": "U1",
  "requestId": "UUID"
}

---

## DB Schema

### tickets
- id
- event_id
- seat_id (unique)
- user_id
- status

### idempotency
- request_id (unique)
- response

---

## Steps to Implement

1. Create Spring Boot project
2. Add Redisson dependency
3. Setup Redis via Docker
4. Create booking API
5. Implement Redisson lock
6. Add DB constraint (unique seat)
7. Add idempotency layer
8. Simulate concurrent requests
9. Add Redlock configuration
10. Test failure scenarios

---

## Test Scenarios

- Multiple concurrent booking requests
- Retry with same requestId
- Redis restart
- Lock expiry

---

## Expected Outcome

- Only one booking succeeds per seat
- Others fail gracefully
- No duplicate entries in DB

---

## Interview Talking Points

- Lock vs Idempotency
- Single node vs distributed lock
- Trade-offs of Redlock
- Failure handling

---

## Future Enhancements

- Add Kafka for async processing
- Add retry/backoff
- Add monitoring (metrics/logs)
