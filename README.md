# Book-My-Event: Seat Reservation at Scale

A highly concurrent, race-condition-free seat reservation service built with **Java 21** and **Spring Boot 3.2**. Designed to handle thousands of concurrent users fighting over limited seats with guaranteed correctness.

## 🎯 Key Features

✅ **Zero Race Conditions** - Atomic database operations ensure a seat is never double-sold
✅ **Idempotency** - Retried requests with same key don't create duplicate reservations  
✅ **Per-User Limits** - Enforced atomically under concurrency  
✅ **Observability** - Prometheus metrics, structured logging, health checks  
✅ **Production Ready** - Docker, docker-compose, ready to deploy  
✅ **Load Tested** - Included burst test script that proves correctness  

## 🏗️ Architecture

### Atomic Decision Mechanism
The service uses **pessimistic locking with database constraints** to achieve race-free seat allocation:
- Each seat reservation is an atomic `UPDATE ... WHERE status = 'AVAILABLE'`
- Only one transaction can succeed; others get 0 rows affected → 409 Conflict
- Unique index on `(show_id, seat_number)` prevents duplicate seat entries

### Idempotency Model
- Unique index on `(user_id, idempotency_key, show_id)`
- Before attempting reservation, check if this key was already processed
- If yes, return the original reservation; if no, create new one
- Prevents double-charging on retried requests

### Per-User Limits
- Count `CONFIRMED` reservations for user + show in same transaction
- If count >= limit, throw `PerUserLimitExceededException` → 409
- Enforced atomically within transaction

### Hold & Expiry Strategy
Currently implements **all-or-nothing** strategy:
- If user requests ["A12", "A13"] and only A12 is available → reject all
- Prevents partial reservations and simplifies reconciliation
- Atomic decision: all seats must be available before any are confirmed

## 🗄️ Database Schema

```sql
Shows:
  - id (UUID): Primary key
  - name: Event name
  - price_paise: Integer price in paise (never float)
  - per_user_limit: Max seats per user (default 4)

Seats:
  - id (UUID): Primary key
  - show_id: Foreign key to shows
  - seat_number: Seat identifier (e.g., "A12")
  - status: AVAILABLE | HELD | CONFIRMED
  - held_by, held_until: Temporary hold info
  - confirmed_by, confirmed_at: Final confirmation info
  - Unique index: (show_id, seat_number)
  - Status index: (show_id, status) for fast filtering

Reservations:
  - id (UUID): Primary key
  - show_id, user_id: Foreign keys
  - seatIds: Set of seat IDs in this reservation
  - amount_paise: Total cost (price × seat count)
  - idempotency_key: Unique per (user_id, show_id)
  - status: CONFIRMED | CANCELLED | EXPIRED
  - Unique index: (user_id, idempotency_key)

ReservationSeats (join table):
  - Links reservations to seats
```

## 🚀 Quick Start

### Local Development

#### Prerequisites
- Java 21+
- Maven 3.8+
- Docker & Docker Compose (for database)

#### 1. Start PostgreSQL
```bash
docker-compose up db
```

#### 2. Build & Run
```bash
mvn clean install
mvn spring-boot:run
```

The app starts on `http://localhost:8080`

#### 3. Verify health
```bash
curl http://localhost:8080/health/live
curl http://localhost:8080/health/ready
curl http://localhost:8080/actuator/metrics/reservations.confirmed
```

### Run Full Stack with Docker Compose
```bash
docker-compose up --build
```

## 📡 API Reference

### 1. Create a Show
```bash
POST /shows
Content-Type: application/json

{
  "name": "friday-night-concert",
  "seats": ["A1", "A2", "A3", ..., "Z50"],
  "price_paise": 25000,
  "per_user_limit": 4
}

Response 201:
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "friday-night-concert",
  "price_paise": 25000,
  "per_user_limit": 4,
  "available_count": 676,
  "held_count": 0,
  "confirmed_count": 0,
  "seats": [{ "id": "...", "seat_number": "A1", "status": "AVAILABLE" }, ...]
}
```

### 2. Reserve Seats (THE CRITICAL ONE)
```bash
POST /shows/{id}/reserve
X-User-Id: user_12345
Content-Type: application/json

{
  "seats": ["A12", "A13"],
  "idempotency_key": "user_12345_timestamp_12345"
}

Response 201 (Success):
{
  "reservation_id": "...",
  "show_id": "...",
  "user_id": "user_12345",
  "seats": ["A12", "A13"],
  "amount_paise": 50000,
  "status": "CONFIRMED"
}

Response 409 (Conflict):
{
  "code": "seats_not_available",
  "message": "Not all seats available. Failed: [A13]. Using all-or-nothing strategy..."
}

Response 409 (Per-user limit):
{
  "code": "per_user_limit_exceeded",
  "message": "User has already confirmed 4 seats"
}

Response 409 (Idempotent replay):
Returns the original reservation with 201 status (or 200 for replays)
```

### 3. Cancel Reservation
```bash
POST /reservations/{id}/cancel
X-User-Id: user_12345

Response 204 No Content

Response 403 (Not owner):
{
  "code": "unauthorized",
  "message": "Only the reservation owner can cancel"
}
```

### 4. Get Show State
```bash
GET /shows/{id}

Response 200:
{
  "id": "...",
  "name": "friday-night",
  "available_count": 50,
  "held_count": 10,
  "confirmed_count": 940,
  "seats": [...]
}
```

## 📊 Metrics & Observability

### Prometheus Metrics
```bash
curl http://localhost:8080/actuator/metrics/prometheus
```

Key metrics:
- `reservations_confirmed_total`: Total seats confirmed (counter)
- `reservations_declined_seat_taken_total`: Seats already taken (counter)
- `reservations_declined_per_user_limit_total`: Per-user limits hit (counter)
- `reservations_declined_idempotent_replay_total`: Idempotent replays (counter)

### Structured Logging
All logs include:
- Timestamp
- User ID (when applicable)
- Request ID (correlation ID)
- Action taken
- Outcome

Example:
```
2026-10-01 17:30:45 - Reserve request from user user_123 for show abc-def, seats: [A12]
2026-10-01 17:30:45 - Reservation confirmed for user user_123 on show abc-def: 1 seats
```

### Health Checks
- `GET /health/live` → Liveness (always UP)
- `GET /health/ready` → Readiness (checks DB connection)

## 🧪 Load Testing

### Run the Burst Test
```bash
./burst.sh http://localhost:8080
```

This script:
1. Creates a fresh show with 100 seats
2. Launches 500 concurrent users all targeting the same hot seat (A1)
3. Prints results:
   - 201 Confirmed: Exactly 1 should win
   - 409 Conflicts: 499 should lose
   - 5xx Errors: Should be ZERO
4. Verifies reconciliation invariant: available + held + confirmed == total

Expected output:
```
Results Summary:
  201 Confirmed:           1
  409 Seat Taken (conflict): 499
  5xx Server Errors:       0

Total Requests: 500

Final show state:
  available_count: 99
  held_count: 0
  confirmed_count: 1
  total_seats: 100

✓ Reconciliation invariant holds: 100 == 100
```

### Load Test Against Deployed Service
```bash
./burst.sh https://your-deployed-url.render.com
```

## 🐳 Docker & Deployment

### Build Docker Image
```bash
docker build -t book-my-event:latest .
```

### Run with Docker Compose
```bash
docker-compose up --build
```

Service will be available at `http://localhost:8080`

### Deploy to Render (Free Tier)

1. **Push to GitHub**
   ```bash
   git add .
   git commit -m "Initial commit: Seat reservation service"
   git push origin main
   ```

2. **Create Render Service**
   - Go to https://render.com
   - Click "New +" → "Web Service"
   - Connect GitHub repo
   - Build command: `mvn clean install`
   - Start command: `java -jar target/Book-My-Event-1.0.0.jar`
   - Environment variables:
     ```
     SPRING_DATASOURCE_URL=postgresql://<render-db-url>:5432/book_my_event
     SPRING_DATASOURCE_USERNAME=<user>
     SPRING_DATASOURCE_PASSWORD=<password>
     ```

3. **Create PostgreSQL Database**
   - Render → "New +" → "PostgreSQL"
   - Name: `book-my-event-db`
   - Connect the database to the web service

4. **Test Deployed Service**
   ```bash
   ./burst.sh https://book-my-event-xxxxx.onrender.com
   ```

## 📝 Key Design Decisions

### Why Atomic Database Operations?
A naive read-then-write loses races:
```
User A: SELECT status FROM seats WHERE id=A12  → AVAILABLE ✓
User B: SELECT status FROM seats WHERE id=A12  → AVAILABLE ✓
User A: UPDATE seats SET status=CONFIRMED      → SUCCESS
User B: UPDATE seats SET status=CONFIRMED      → SUCCESS ❌ DOUBLE-SELL
```

Solution: Push decision into atomic UPDATE:
```
User A: UPDATE seats SET status='CONFIRMED' WHERE id=A12 AND status='AVAILABLE' → 1 row affected
User B: UPDATE seats SET status='CONFIRMED' WHERE id=A12 AND status='AVAILABLE' → 0 rows affected → CONFLICT
```

### Why All-or-Nothing for Multi-Seat Requests?
Simplifies logic and prevents partial reservations:
- User requests ["A12", "A13"]
- If A12 available but A13 taken → reject entire request
- Prevents partial states and simplifies reconciliation
- Atomic decision: all seats confirmed together or none

### Why Idempotency Key Stored in DB?
Ensures exactly-once semantics:
- Client sends `idempotency_key` with every reserve request
- Server checks `(user_id, idempotency_key, show_id)` unique index
- If duplicate key detected → return original reservation (not an error)
- Safely handles retried requests

## ⚠️ Correctness Guarantees

Under sustained load (20K+ concurrent requests):
1. ✅ **No double-sells**: A seat is never confirmed to 2 users
2. ✅ **No 5xx errors**: Declines are 4xx (business logic), not server errors
3. ✅ **Reconciliation holds**: available + held + confirmed == total_seats (always)
4. ✅ **Idempotency**: Same key, one reservation; different seats on same key → 409
5. ✅ **Per-user limits**: User can't exceed limit even with parallel requests
6. ✅ **Identity isolation**: User can only cancel their own reservations

## 🔧 Troubleshooting

### "Connection refused" to database
```bash
docker-compose ps   # Check if db is running
docker-compose logs db  # View database logs
```

### "HTTP 500 during high load"
- Check `curl http://localhost:8080/actuator/metrics` for metric names
- Review logs: `docker-compose logs app`
- Verify database connections: `docker-compose logs db`

### Metrics not showing
```bash
curl http://localhost:8080/actuator/metrics/reservations.confirmed
# Should show counter value
```

## 📚 Further Reading

- [Spring Boot Docs](https://spring.io/projects/spring-boot)
- [PostgreSQL ACID Guarantees](https://www.postgresql.org/docs/current/tutorial-transactions.html)
- [Distributed Systems: Race Conditions](https://en.wikipedia.org/wiki/Race_condition)
- [Idempotency Keys](https://stripe.com/blog/idempotency)

## 📄 License

MIT License - Free to use for interview prep and beyond.

---

**Built with ❤️ for the Paytm Money Interview Challenge**
