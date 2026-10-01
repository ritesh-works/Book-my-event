# Interview Preparation Guide

## 🎯 What They're Really Testing

1. **Concurrency Understanding** - Can you avoid race conditions under load?
2. **Database Design** - Do you know how to make atomic decisions in SQL?
3. **System Design** - Can you build a service that's correct AND observable?
4. **Production Readiness** - Does it actually run? Can you deploy it?
5. **Communication** - Can you explain your design decisions clearly?

## 📚 Study Materials (What to Know)

### Concurrency & Race Conditions

**Question**: "How do you prevent double-selling?"
**Answer to give**:
```
We use atomic database operations. Instead of:
  1. SELECT to check if seat is available
  2. INSERT/UPDATE to claim it
  
We do this in ONE atomic operation:
  UPDATE seats SET status='CONFIRMED' 
  WHERE seat_id=? AND status='AVAILABLE'
  
Only one transaction can succeed. Others get 0 rows affected → 409 Conflict.
No race condition possible because the decision is made at the database level.
```

**Question**: "What about multi-seat requests like ['A12', 'A13']?"
**Answer to give**:
```
We use all-or-nothing strategy within a single @Transactional block:
1. Sort seats deterministically (alphabetically)
2. Check all seats in order
3. Lock them pessimistically via the transaction
4. If any seat unavailable, rollback entire transaction
5. Otherwise commit all seat updates atomically

This prevents partial reservations and deadlocks.
```

### Idempotency

**Question**: "How do you handle retried requests?"
**Answer to give**:
```
Before attempting any reservation:
1. Check database for (user_id, idempotency_key, show_id) tuple
2. If exists, return the original reservation (cached result)
3. If not, proceed with new reservation
4. Store result with idempotency key so retries are safe

This ensures exactly-once semantics even if client retries 100 times.
```

### Per-User Limits

**Question**: "How do you enforce per-user limits under concurrency?"
**Answer to give**:
```
Count CONFIRMED reservations in the SAME transaction as seat confirmation:

@Transactional
public void reserve() {
    long count = SELECT COUNT(*) FROM reservations 
                 WHERE user_id=? AND show_id=? AND status='CONFIRMED';
    
    if (count >= limit) throw PerUserLimitExceededException;
    
    // Update seats
    UPDATE seats SET status='CONFIRMED' WHERE ...
    
    // Insert reservation
    INSERT INTO reservations ...
}

All in one transaction. No race possible.
```

### Observability

**Question**: "How do you know if things are working correctly?"
**Answer to give**:
```
Three pillars:

1. Metrics (Prometheus):
   - reservations_confirmed_total (counter)
   - reservations_declined_seat_taken_total (counter)
   - reservations_declined_per_user_limit_total (counter)
   
2. Logs (Structured):
   - Every request has correlation ID
   - Log user_id, action, outcome
   - Can trace specific user's journey
   
3. Health Checks:
   - /health/live (is service running?)
   - /health/ready (is database reachable?)
   
Result: At 2am, one metric spike tells me exactly what's wrong.
```

## 🎤 Practice These Explanations

### Explanation 1: Why Not SELECT + UPDATE?

**They might ask**: "Why can't you just SELECT to check, then UPDATE if free?"

**Perfect answer**:
```
Thread A: SELECT * FROM seats WHERE id='A12' → status='AVAILABLE'
Thread B: SELECT * FROM seats WHERE id='A12' → status='AVAILABLE'  [before A's update]
Thread A: UPDATE seats SET status='CONFIRMED' WHERE id='A12' → SUCCESS
Thread B: UPDATE seats SET status='CONFIRMED' WHERE id='A12' → SUCCESS ❌ 

Both threads see the seat as available before either updates it. 
By the time B updates, A has already claimed it. Double-sell.

Solution: Push the check into the UPDATE itself:
UPDATE seats SET status='CONFIRMED' 
WHERE id='A12' AND status='AVAILABLE'

Now only ONE thread can succeed. Others get 0 rows affected.
```

### Explanation 2: All-or-Nothing vs Best-Effort

**They might ask**: "Why reject the whole request if only 1 seat is taken?"

**Good answer**:
```
Two strategies:

1. ALL-OR-NOTHING (what we chose):
   - User requests ['A12', 'A13']
   - If either is unavailable → reject entire request
   - Simpler, atomic, predictable
   - No partial reservations to manage
   
2. BEST-EFFORT (alternative):
   - Confirm whatever is available
   - Return both successes and failures
   - More complex code, partial state to track
   - Better UX (user gets some seats, not none)

Trade-off: We chose simplicity and atomicity. For an interview problem, 
all-or-nothing is the right call. In production, might switch to best-effort 
with more complex partial reservation tracking.
```

### Explanation 3: Consistency vs Availability

**They might ask**: "What if the database goes down during the burst?"

**Perfect answer**:
```
CAP theorem: Consistency, Availability, Partition Tolerance (pick 2)

We chose: Consistency + Availability (sacrifice partition tolerance)

If database becomes unavailable:
1. Health check /health/ready returns DOWN
2. Load balancer removes us from rotation
3. New requests fail immediately with 503
4. Users don't wait; they try another instance or retry later

We do NOT:
- Keep serving stale data (loses consistency)
- Accept writes to local cache (loses correctness)

This is the right choice for financial transactions 
(seats, money). Always fail closed rather than sell the same seat twice.
```

## 🧠 Questions They'll Ask (And How to Answer)

### Q1: "What if the unique constraint on (user_id, idempotency_key) fails?"

**Answer**:
```
The database will reject the duplicate INSERT with a constraint violation.
We catch this exception and return the original reservation.
Same result: idempotency is preserved.

Actually, we check BEFORE attempting insert, so the exception is a safety net.
```

### Q2: "What about timestamp skew between servers?"

**Answer**:
```
Good question. We don't use local time for critical decisions.
- Seat status: Atomic in DB, no timestamps needed
- Hold expiry: Uses server (DB) timestamp via NOW() in SQL
- Idempotency: Uses DB timestamp for created_at

No clock skew issues.
```

### Q3: "Can a user hold more seats than the per-user limit?"

**Answer**:
```
No. The limit is on CONFIRMED reservations.
1. Count check happens atomically in same transaction
2. If user has 4 confirmed, the 5th reservation throws exception
3. Even with 10 parallel requests, max 4 can succeed

This works because of REPEATABLE_READ isolation level.
```

### Q4: "What if the show doesn't exist?"

**Answer**:
```
The API returns 404 Not Found immediately.
We check showRepository.findById(id).orElseThrow(...);
Happens before any seat logic, fail-fast approach.
```

### Q5: "How do you handle database connection failures?"

**Answer**:
```
Spring Boot + HikariCP handle this:
1. Failed connection → exception thrown
2. /health/ready detects DB is down → returns DOWN
3. Load balancer stops routing traffic
4. Users get 503 Service Unavailable immediately
5. No partial reservations created

Fail-closed approach. Correct over available.
```

### Q6: "What's the worst-case latency under load?"

**Answer**:
```
With 20K concurrent requests:
1. Database queue time: depends on CPU, disk, indexes
2. Our latency: minimal (just SQL execution + Flyway validation)
3. Worst case: ~200-500ms per request (rough estimate)

If latency becomes unacceptable:
- Add read replicas for GET requests
- Cache seat availability (with careful invalidation)
- Shard by show_id (different DB per show)
- Use event streaming (Kafka) for async processing

But for this scale (20K total, not concurrent), single DB is fine.
```

### Q7: "What about database transaction locks?"

**Answer**:
```
Locks are automatically managed by PostgreSQL:
1. SELECT lock: Row-level, released at end of transaction
2. UPDATE lock: Exclusive, prevents other updates
3. Transaction isolation level (REPEATABLE_READ):
   - Prevents dirty reads (read uncommitted changes)
   - Prevents non-repeatable reads (data changes mid-transaction)
   - Allows phantom reads (new rows appear, but we don't query them)

Deadlock prevention:
- We lock seats in deterministic order (sorted by seat number)
- Consistent ordering prevents circular waits
- Single transaction = single lock acquisition phase

PostgreSQL automatically detects and rolls back deadlocks (rare).
```

## 🎬 Live Demo Script

When they ask you to extend the system, here's what you'd do:

### Extension 1: "Add a cancellation deadline (can't cancel after 1 hour)"

```java
@Column
private LocalDateTime cancellableUntil;

@PrePersist
protected void onCreate() {
    this.cancellableUntil = LocalDateTime.now().plusHours(1);
}

@Transactional
public void cancelReservation(String reservationId, String userId) {
    Reservation res = reservationRepository.findById(reservationId).orElseThrow();
    
    if (LocalDateTime.now().isAfter(res.getCancellableUntil())) {
        throw new CancellationDeadlinePassedException("Too late to cancel");
    }
    
    if (!res.getUserId().equals(userId)) {
        throw new UnauthorizedException("Not your reservation");
    }
    
    // Release seats...
}
```

### Extension 2: "Add seat pricing (some seats cost more)"

```java
@Column
private long pricePaise;  // Price for THIS seat, may differ

@Transactional
public ReservationDTO reserveSeats(...) {
    long totalAmount = 0;
    
    for (String seatNumber : requestedSeats) {
        Seat seat = seatRepository.findBySeatNumber(seatNumber);
        totalAmount += seat.getPricePaise();  // Add this seat's price
    }
    
    // Proceed as normal, but update amountPaise calculation
    reservation.setAmountPaise(totalAmount);
}
```

### Extension 3: "Add a waitlist when show is full"

```java
@Entity
public class WaitlistEntry {
    private String userId;
    private String showId;
    private int position;
    private LocalDateTime createdAt;
}

@Transactional
public ReservationDTO reserveSeats(...) {
    for (String seatNumber : requestedSeats) {
        Seat seat = seatRepository.findBySeatNumber(seatNumber);
        
        if (seat.getStatus() == AVAILABLE) {
            // Normal flow
        } else if (seat.getStatus() == CONFIRMED) {
            // Add to waitlist
            WaitlistEntry entry = WaitlistEntry.builder()
                .userId(userId)
                .showId(showId)
                .position(waitlistRepository.getNextPosition(showId))
                .build();
            waitlistRepository.save(entry);
            
            return ReservationDTO.builder()
                .status("WAITLISTED")
                .position(entry.getPosition())
                .build();
        }
    }
}
```

## ✅ Interview Day Checklist

### Before the Interview
- [ ] Read through WRITEUP.md 2-3 times
- [ ] Understand the atomic UPDATE logic deeply
- [ ] Practice explaining idempotency in 30 seconds
- [ ] Know the deployment steps by heart
- [ ] Have the GitHub link ready
- [ ] Test burst.sh locally to see it work

### During the Interview
- [ ] Listen carefully to the problem statement
- [ ] Ask clarifying questions (they love this)
- [ ] Draw pictures/diagrams when explaining race conditions
- [ ] Explain your assumptions clearly
- [ ] Walk through specific scenarios (1 user vs 500 users)
- [ ] Mention trade-offs you made (all-or-nothing vs best-effort)

### When They Ask to Extend
- [ ] Think out loud (they want to see your process)
- [ ] Ask for clarification
- [ ] Propose multiple approaches, then explain why you'd choose one
- [ ] Code confidently; don't apologize for syntax
- [ ] Explain how your change maintains correctness under load

### Show Them
- [ ] Live deployment (if you have time)
- [ ] Running the burst test
- [ ] The git history (clean commits, good messages)
- [ ] Metrics endpoint showing results

## 🎓 Key Takeaways for Interview

1. **The core insight**: "Push atomic decisions into the database, not the application"
2. **Why it works**: "Only one thread can satisfy the WHERE clause; others get 0 rows"
3. **Idempotency**: "Check before write; return cached result on replay"
4. **Per-user limits**: "Count in same transaction as the update"
5. **Observability**: "Metrics + logs tell you exactly what's broken"

## 🚀 During the Interview (If They Ask to Code Live)

**They might say**: "Add a feature to see how many people are ahead of you in a hot seat"

**Your approach**:
```
1. Think out loud: "I need to query how many users are already trying for this seat"
2. Design: Add a view/query that counts active reservations for a seat
3. API endpoint: GET /shows/{id}/seats/{seat}/contention
4. Response: {"users_ahead": 127, "rank": 128}
5. Correctness: Query uses current snapshot; number changes constantly (acceptable)
```

**They might say**: "What if two users want to cancel at the same time?"

**Your approach**:
```
1. Identify the race: Both execute UPDATE simultaneously
2. Solution: Atomic UPDATE that checks status
   UPDATE reservations SET status='CANCELLED'
   WHERE id=? AND user_id=? AND status='CONFIRMED'
3. First succeeds, releases seats; second gets 0 rows → fails with 404/410
4. Idempotent: Retrying a cancel returns 410 Gone (already cancelled)
```

---

**Good luck! You've got this.** 🚀

The key is to show you understand **concurrency deeply**, **database design**, and **production systems**. This implementation demonstrates all three. Trust your preparation, explain clearly, and code confidently.
