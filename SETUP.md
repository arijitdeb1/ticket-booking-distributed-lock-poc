# Setup Guide — Ticket Booking Distributed Lock POC

## Prerequisites (mandatory)

| Tool | Version | Notes |
|------|---------|-------|
| Java | 17 | `java -version` must show 17 |
| Maven | 3.8+ | `mvn -version` |
| Docker Desktop | latest | For Redis containers |

---

## Variant 1 — Redisson Single Redis

### Step 1: Start Redis via Docker

```bash
docker-compose up -d redis
```

Verify:
```bash
docker ps
docker exec redis-single redis-cli ping   # → PONG
```

### Step 2: Build the project

```bash
mvn clean package -DskipTests
```

### Step 3: Run the application

```bash
mvn spring-boot:run
# or
java -jar target/ticket-booking-distributed-lock-poc-0.0.1-SNAPSHOT.jar
```

App starts on **http://localhost:8080**

### Step 4: H2 Console (in-memory DB inspection)

Open: http://localhost:8080/h2-console

| Field | Value |
|-------|-------|
| JDBC URL | `jdbc:h2:mem:ticketdb` |
| Username | `sa` |
| Password | *(empty)* |

### Step 5: Test the API

**Book a ticket:**
```bash
curl -X POST http://localhost:8080/api/book \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "E1",
    "seatId": "S1",
    "userId": "U1",
    "requestId": "550e8400-e29b-41d4-a716-446655440000"
  }'
```

**Expected responses:**

| Scenario | `status` field |
|----------|---------------|
| First booking | `BOOKED` |
| Same seat, different requestId | `ALREADY_BOOKED` |
| Same requestId (retry) | `BOOKED` (cached — idempotent) |
| Lock contention | `LOCK_UNAVAILABLE` |

### Step 6: Simulate concurrent requests (optional)

```bash
# Fire 10 concurrent requests for the same seat
for i in $(seq 1 10); do
  curl -s -X POST http://localhost:8080/api/book \
    -H "Content-Type: application/json" \
    -d "{\"eventId\":\"E1\",\"seatId\":\"S2\",\"userId\":\"U$i\",\"requestId\":\"$(uuidgen)\"}" &
done
wait
```

Only one request should return `BOOKED`; all others return `ALREADY_BOOKED` or `LOCK_UNAVAILABLE`.

---

## Variant 2 — Redlock (Multi Redis) [Future]

1. Uncomment the three Redis services in `docker-compose.yml`.
2. Activate Spring profile `redlock`:
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=redlock
   ```
3. Implement `RedlockLockService` (annotated `@Profile("redlock")`) — the `DistributedLockService` interface is already in place.

---

## Key Configuration Files

| File | Purpose |
|------|---------|
| `pom.xml` | Maven deps — Spring Boot 3.2.5, Redisson 3.27.2, H2, PostgreSQL |
| `src/main/resources/application.yml` | App config, H2 datasource, JPA |
| `src/main/resources/redisson.yml` | Redisson single-node config (auto-loaded by starter) |
| `src/main/resources/application-redlock.yml` | Overrides for Variant 2 (PostgreSQL + multi-Redis) |
| `docker-compose.yml` | Redis containers |

---

## Lock Design

- **Lock key format:** `ticket:lock:{eventId}:{seatId}`
- **Lease time:** 10 seconds (prevents deadlock if app crashes)
- **Wait time:** 0 ms (fail-fast; no queuing)
- **Idempotency key:** `requestId` (UUID) stored in `idempotency` table

---

## Actuator Health

```
GET http://localhost:8080/actuator/health
```
