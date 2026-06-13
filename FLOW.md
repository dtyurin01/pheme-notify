# Pheme Notify — Application Flow

> Detailed description of notification processing: from incoming Kafka event to database record.

---

## Overview

```
Kafka event
    │
    ▼
NotificationEventConsumer       ← @RetryableTopic (3x, backoff 5s/10s/20s)
    │
    ▼
NotificationOrchestrator
    │
    ├─► RedisDeduplicationAdapter   → Redis SETNX "dedup:{id}" TTL=24h
    │       duplicate? → STOP
    │
    ├─► UserPreferenceRepository    → PostgreSQL: which channels are enabled
    │       no preferences? → STOP
    │
    └─► for each channel (partial failure — channels are independent):
            │
            ├─► NotificationRepository.save(PENDING)
            ├─► RedisRateLimitAdapter        → Lua sliding window
            │       exceeded? → FAILED, next channel
            ├─► TemplateService              → Thymeleaf render
            ├─► ProviderRegistry.getProvider(channel)
            │       EmailProvider            → Circuit Breaker → JavaMailSender → Mailpit
            │       SmsProvider / PushProvider → mock (log)
            ├─► NotificationMetrics           → sent/failed counters + send duration timer
            └─► NotificationRepository.save(DELIVERED / FAILED)

if all retries failed:
    DLT → handleDlt() → failed_notifications (JSONB)
              ▲
              │ every N seconds
    FailedNotificationRetryScheduler → processRetry()
```

---

## Step 1 — Incoming Kafka event

**File:** `src/main/java/com/pheme/phemenotify/messaging/consumer/NotificationEventConsumer.java`

Kafka topic `notification.events` accepts three event types:
- `order.completed`
- `user.registered`
- `payment.failed`

Each event is deserialized into a `NotificationEvent` record:

```java
public record NotificationEvent(
    String id,           // UUID — deduplication key
    String userId,
    EventType eventType,
    Channel channel,
    Map<String, String> payload,
    Instant occurredAt
) {}
```

`@RetryableTopic` automatically creates retry topics and configures exponential backoff:

| Attempt | Topic | Delay |
|---------|-------|-------|
| 1 | `notification.events` | — |
| 2 | `notification.events-retry-0` | 5 sec |
| 3 | `notification.events-retry-1` | 10 sec |
| DLT | `notification.events.dlt` | after 3rd failure |

---

## Step 2 — Orchestrator runs the pipeline

**File:** `src/main/java/com/pheme/phemenotify/service/NotificationOrchestrator.java`

Central service. Receives the event from the consumer and sequentially calls all steps.
Implements the **Partial Failure** pattern: channels are processed in separate `try/catch` blocks.
An error in EMAIL does not stop SMS.

---

## Step 3 — Deduplication (Hard #1)

**File:** `src/main/java/com/pheme/phemenotify/infrastructure/redis/RedisDeduplicationAdapter.java`

Redis command `SETNX` atomically writes a key only if it doesn't exist:

```
SETNX dedup:{eventId} "1" EX 86400
```

| SETNX result | Meaning | Action |
|----------------|----------|----------|
| `true` | key didn't exist → new event | continue |
| `false` | key already existed → duplicate | skip |

TTL = 24 hours. If Kafka delivers the same event twice (at-least-once guarantee),
the second occurrence is ignored. This provides **effectively exactly-once** semantics.

---

## Step 4 — Loading user preferences

**File:** `src/main/java/com/pheme/phemenotify/persistence/repository/UserPreferenceRepository.java`

```sql
SELECT * FROM user_preferences WHERE user_id = :userId
```

Returns `UserPreferences` — list of enabled channels, e.g. `[EMAIL, SMS]`.

- No preferences found → `log.warn` → processing stops
- All channels disabled → `log.warn` → processing stops

---

## Step 5 — Processing each channel

**File:** `src/main/java/com/pheme/phemenotify/service/NotificationOrchestrator.java` — method `processChannel()`

For each channel in `UserPreferences.enabledChannels`, steps 5a–5e are executed.
If a channel fails, move on to the next channel — no exception propagates upward.

---

### Step 5a — Save PENDING record

**File:** `src/main/java/com/pheme/phemenotify/persistence/repository/NotificationRepository.java`

Before sending, save a record in the `notifications` table with status `PENDING`.

Idempotency key: `{eventId}:{channel}` — `UNIQUE` constraint in the DB.
If the record already exists (`DataIntegrityViolationException`) → duplicate, skip the channel.
This guards against race conditions during parallel Kafka retries.

---

### Step 5b — Rate Limit (Hard #2)

**Files:**
- `src/main/java/com/pheme/phemenotify/service/RateLimitService.java`
- `src/main/java/com/pheme/phemenotify/infrastructure/redis/RedisRateLimitAdapter.java`
- `src/main/resources/redis/rate_limit.lua`

`RedisRateLimitAdapter` runs the Lua script **atomically** via `redisTemplate.execute()`.
Redis key: `ratelimit:{userId}:{channel}`.

The Lua script performs 4 operations in one transaction:

```lua
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)  -- remove old entries
local count = redis.call('ZCARD', key)                 -- count within window
if count >= max_requests then return 1 end             -- block
redis.call('ZADD', key, now, now .. ':' .. random)     -- add current
redis.call('PEXPIRE', key, window)                     -- refresh TTL
return 0                                               -- allow
```

Limits per channel:

| Channel | Limit | Window |
|-------|-------|------|
| EMAIL | 5 | 1 hour |
| SMS | 3 | 1 hour |
| PUSH | 20 | 1 hour |

Why Lua and not Java? Multiple Redis commands from Java create a race condition:
another thread could interleave between `ZCARD` and `ZADD` and corrupt the counter.

On exceeding the limit: `status = FAILED`, `errorMessage = "RATE_LIMIT_EXCEEDED"`, move to the next channel.

---

### Step 5c — Template rendering

**File:** `src/main/java/com/pheme/phemenotify/service/TemplateService.java`

Thymeleaf renders the template at path `templates/{channel}/{eventType}`.

Path = `templates/{channel}/{event-type-code-kebab-case}.{ext}` (`.html` for EMAIL, `.txt` for SMS/PUSH).

Examples:
- `templates/email/order-completed.html`
- `templates/sms/user-registered.txt`
- `templates/push/payment-failed.txt`

All 3 event types (`order.completed`, `user.registered`, `payment.failed`) have templates for all 3 channels (email/sms/push).

The event's `payload` is passed to the template — it may contain the user's name,
order total, verification code, etc.

---

### Step 5d — Sending via provider

**Files:**
- `src/main/java/com/pheme/phemenotify/provider/ProviderRegistry.java`
- `src/main/java/com/pheme/phemenotify/provider/EmailProvider.java`
- `src/main/java/com/pheme/phemenotify/provider/SmsProvider.java`
- `src/main/java/com/pheme/phemenotify/provider/PushProvider.java`

`ProviderRegistry` is a `Map<Channel, NotificationProvider>`. **Strategy** pattern:
look up the implementation by channel.

**EmailProvider** wraps sending in a **Circuit Breaker** (Resilience4j):

| CB state | What happens |
|-------------|---------------|
| `CLOSED` | emails go through `JavaMailSender` → Mailpit (dev) |
| `OPEN` | calls fail immediately without attempting SMTP connection |
| `HALF_OPEN` | after timeout, allows one test request through |

CB transitions to `OPEN` after N consecutive failures — protects against a
request storm to an unavailable SMTP server.

**SmsProvider** and **PushProvider** are mocks: they log the sent message, with no real integration.

---

### Step 5e — Updating status in DB

**File:** `src/main/java/com/pheme/phemenotify/persistence/repository/NotificationRepository.java`

| Result | Status | Fields |
|-----------|--------|--------|
| Success | `DELIVERED` | `sent_at = now()` |
| Error | `FAILED` | `error_message = "SEND_FAILED:RuntimeException"` |

`error_message` holds a normalized code, not `e.getMessage()`.
Exception details go only to logs (SLF4J automatically logs the stacktrace).

**Metrics (Micrometer, `NotificationMetrics`):**
- `notifications.sent{channel}` — counter, incremented on DELIVERED
- `notifications.failed{channel}` — counter, incremented on FAILED (at send step)
- `notification.send.duration{channel}` — timer (with percentile histogram) around template rendering + provider call, via `Timer.Sample` in `try/finally`

---

## Step 6 — DLT: Dead Letter Topic

**File:** `src/main/java/com/pheme/phemenotify/messaging/consumer/NotificationEventConsumer.java` — method `handleDlt()`

If all 3 retries are exhausted, Spring Kafka automatically routes the message
to `notification.events.dlt`. The `@DltHandler` method receives the event and saves
it to the `failed_notifications` table with the full `event_payload` in JSONB:

```json
{
  "id": "uuid",
  "userId": "user-123",
  "eventType": "order.completed",
  "channel": "EMAIL",
  "occurredAt": "2026-05-30T10:00:00Z",
  "payload": { "email": "user@example.com" }
}
```

JSONB allows the event to be fully reconstructed for a retry.

---

## Step 7 — Scheduled Retry

**File:** `src/main/java/com/pheme/phemenotify/messaging/retry/FailedNotificationRetryScheduler.java`

`@Scheduled` runs every N seconds (configurable via `RetrySchedulerProperties`).
Queries `failed_notifications` for records where `status = PENDING` and `next_retry_at < now()`.

Exponential backoff:

| Attempt | Next retry after |
|---------|------------------------|
| 1 | base interval (e.g. 1 min) |
| 2 | ×2 |
| 3 | ×4 |
| N >= maxAttempts | status permanently `FAILED` |

Reconstructs the `NotificationEvent` from JSONB and calls
`NotificationOrchestrator.processRetry()` — the same pipeline, but **without deduplication**
(this isn't a new event, the dedup step is skipped).

---

## Parallel flows — REST API

### Analytics API (Hard #3)

**Files:**
- `src/main/java/com/pheme/phemenotify/api/controller/AnalyticsController.java`
- `src/main/java/com/pheme/phemenotify/service/AnalyticsService.java`
- `src/main/java/com/pheme/phemenotify/persistence/repository/NotificationRepository.java`

```
GET /api/v1/analytics/delivery-stats?startDate=2026-05-01&endDate=2026-05-30
    │
    ▼
AnalyticsService
    │
    ├─► Redis cache (@Cacheable, key: "startDate:endDate", TTL=1h)
    │       cache hit  → return immediately
    │       cache miss → run SQL
    │
    └─► NotificationRepository.findDeliveryStats()
            native SQL:
            - DATE_TRUNC('day', created_at) — group by day
            - COUNT(*) FILTER (WHERE status = 'DELIVERED') — stats
            - AVG(delivery_rate) OVER (PARTITION BY channel, event_type
              ORDER BY day ROWS BETWEEN 6 PRECEDING AND CURRENT ROW)
              — 7-day rolling average
```

### Preferences API

```
GET  /api/v1/users/{id}/preferences  → PreferenceController → PreferenceService → UserPreferenceRepository
PUT  /api/v1/users/{id}/preferences  → validation → save enabledChannels
```

### Notification Status API

```
GET /api/v1/notifications/{id}/status → NotificationController → NotificationRepository.findById()
```

---

## Error handling

**File:** `src/main/java/com/pheme/phemenotify/api/exception/GlobalExceptionHandler.java`

All HTTP errors are returned in **Problem Detail (RFC 9457)** format:

```json
{
  "type": "https://pheme.com/errors/rate-limit-exceeded",
  "title": "Rate Limit Exceeded",
  "status": 429,
  "detail": "Email limit: 5/hour exceeded for user 123"
}
```

Stacktrace is **never** returned to the client. Unexpected errors are logged with a UUID:
`log.error("Unexpected error [id={}]", errorId, exception)` — the UUID can be used to find
the full stacktrace in the logs.

---

## Infrastructure configuration

| Service | Port | Purpose |
|--------|------|-----------|
| App | 8080 | REST API + Actuator |
| Kafka | 9092 | Incoming events |
| Kafka UI | 8090 | Topic/message monitoring |
| PostgreSQL | 55200 | Stores notifications, preferences |
| Redis | 6379 | Dedup, Rate Limit, Analytics cache |
| Mailpit SMTP | 1025 | Email interception in dev |
| Mailpit UI | 8025 | View sent emails |
| Prometheus | 9090 | Metrics collection |
| Grafana | 3000 | Dashboards: sent/failed/duration/CB state |
