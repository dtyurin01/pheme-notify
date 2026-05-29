<!-- refreshed: 2026-05-24 -->
# Architecture

**Analysis Date:** 2026-05-24

## System Overview

```text
┌────────────────────────────────────────────────────────────────────┐
│                         Entry Points                               │
├──────────────────────────────┬─────────────────────────────────────┤
│  Kafka Consumer              │  REST API                           │
│  `messaging/consumer/`       │  `api/controller/`                  │
│  NotificationEventConsumer   │  NotificationController             │
│                              │  PreferenceController               │
└──────────────┬───────────────┴──────────────────┬──────────────────┘
               │                                   │
               ▼                                   ▼
┌──────────────────────────────────────────────────────────────────┐
│                         Service Layer                            │
│                                                                  │
│  NotificationOrchestrator   DeduplicationService                 │
│  `service/`                 RateLimitService                     │
│                             TemplateService                      │
│                             NotificationService                  │
│                             PreferenceService                    │
└───────────┬─────────────────────────┬────────────────────────────┘
            │                         │
     ┌──────┘                         └──────┐
     ▼                                       ▼
┌────────────────────┐          ┌────────────────────────────────┐
│  Provider Layer    │          │  Infrastructure Layer          │
│  `provider/`       │          │  `infrastructure/redis/`       │
│  NotificationProvider (iface) │  RedisDeduplicationAdapter     │
│  EmailProvider     │          │  RedisRateLimitAdapter         │
│  SmsProvider       │          └────────────┬───────────────────┘
│  PushProvider      │                       │
│  ProviderRegistry  │                       │
└────────┬───────────┘                       │
         │                                   │
         ▼                                   ▼
┌──────────────────────────────────────────────────────────────────┐
│                     Persistence Layer                            │
│  `persistence/entity/`        `persistence/repository/`         │
│  Notification                 NotificationRepository             │
│  UserPreferences              UserPreferenceRepository           │
│  FailedNotification           FailedNotificationRepository       │
│  `persistence/projection/`    `persistence/converter/`           │
│  DeliveryStatsProjection      EventTypeAttributeConverter        │
└──────────────────────────────────────────────────────────────────┘
         │                                   │
         ▼                                   ▼
┌──────────────────┐           ┌─────────────────────┐
│   PostgreSQL 17  │           │   Redis              │
│  `notifications` │           │  dedup:{id}          │
│  `user_prefs`    │           │  ratelimit:{u}:{ch}  │
│  `failed_notifs` │           └─────────────────────┘
└──────────────────┘
```

## Component Responsibilities

| Component | Responsibility | File |
|-----------|----------------|------|
| `NotificationEventConsumer` | Kafka listener entry point; @RetryableTopic + @DltHandler | `messaging/consumer/NotificationEventConsumer.java` |
| `NotificationOrchestrator` | Core pipeline: dedup → preferences → per-channel rate-limit/template/send | `service/NotificationOrchestrator.java` |
| `NotificationService` | Query-side: fetch notification status for REST API | `service/NotificationService.java` |
| `PreferenceService` | CRUD for user channel preferences | `service/PreferenceService.java` |
| `DeduplicationService` | (thin wrapper — direct adapter use in orchestrator) | `infrastructure/redis/RedisDeduplicationAdapter.java` |
| `RateLimitService` | Delegates per-channel limit check to Redis Lua adapter | `service/RateLimitService.java` |
| `TemplateService` | Resolves and renders Thymeleaf template by eventType + channel | `service/TemplateService.java` |
| `ProviderRegistry` | Strategy map: `Channel → NotificationProvider` | `provider/ProviderRegistry.java` |
| `EmailProvider` | JavaMailSender + Resilience4j CircuitBreaker | `provider/EmailProvider.java` |
| `SmsProvider` | Mock provider: logs + simulated delay | `provider/SmsProvider.java` |
| `PushProvider` | Mock provider | `provider/PushProvider.java` |
| `RedisDeduplicationAdapter` | SETNX dedup:{eventId} TTL=24h | `infrastructure/redis/RedisDeduplicationAdapter.java` |
| `RedisRateLimitAdapter` | Executes sliding-window Lua script via `DefaultRedisScript<Long>` | `infrastructure/redis/RedisRateLimitAdapter.java` |
| `FailedNotificationRetryScheduler` | @Scheduled job: retries PENDING failed_notifications with exponential backoff | `messaging/retry/FailedNotificationRetryScheduler.java` |
| `GlobalExceptionHandler` | Problem Detail RFC 9457 for all REST errors | `api/exception/GlobalExceptionHandler.java` |
| `EventTypeRegistry` | Spring-managed map of `EventType` implementations; drives DB conversion + deserialization | `persistence/entity/EventTypeRegistry.java` |

## Pattern Overview

**Overall:** Event-Driven Pipeline with Layered Architecture

**Key Characteristics:**
- Single Kafka topic consumed by one consumer; orchestration done synchronously within the consumer thread
- Partial failure model: per-channel try/catch in `NotificationOrchestrator.processChannel()` — one channel failure never aborts others
- Strategy pattern for provider dispatch via `ProviderRegistry` (Map<Channel, NotificationProvider>)
- Registry pattern for `EventType` extensibility — new event types added as `@Component` beans, no code changes required in routing
- Adapter pattern isolates Redis operations (`RedisDeduplicationAdapter`, `RedisRateLimitAdapter`) from service logic
- All external failures surface as normalized error codes stored in DB (`"SEND_FAILED:RuntimeException"`, `"RATE_LIMIT_EXCEEDED"`), never raw exception messages

## Layers

**Messaging Layer:**
- Purpose: Consume Kafka events, trigger retry scheduling
- Location: `src/main/java/com/pheme/phemenotify/messaging/`
- Contains: `NotificationEventConsumer`, `NotificationEvent` record, `FailedNotificationRetryScheduler`
- Depends on: Service layer (`NotificationOrchestrator`), Persistence layer (`FailedNotificationRepository`)
- Used by: Nothing (entry point)

**API Layer:**
- Purpose: REST endpoints; DTO mapping; error handling
- Location: `src/main/java/com/pheme/phemenotify/api/`
- Contains: Controllers, request/response DTOs, `GlobalExceptionHandler`, exception classes, `ApiPaths`
- Depends on: Service layer
- Used by: Nothing (entry point)

**Service Layer:**
- Purpose: Business logic orchestration; pipeline composition
- Location: `src/main/java/com/pheme/phemenotify/service/`
- Contains: `NotificationOrchestrator`, `NotificationService`, `PreferenceService`, `RateLimitService`, `TemplateService`
- Depends on: Infrastructure layer, Provider layer, Persistence layer, Config
- Used by: Messaging layer, API layer

**Provider Layer:**
- Purpose: Channel-specific notification delivery; circuit breaker wrapping
- Location: `src/main/java/com/pheme/phemenotify/provider/`
- Contains: `NotificationProvider` interface, `EmailProvider`, `SmsProvider`, `PushProvider`, `ProviderRegistry`
- Depends on: `JavaMailSender`, `CircuitBreakerFactory`
- Used by: `NotificationOrchestrator` (via `ProviderRegistry`)

**Infrastructure Layer:**
- Purpose: Low-level Redis adapter implementations (dedup + rate limit)
- Location: `src/main/java/com/pheme/phemenotify/infrastructure/`
- Contains: `RedisDeduplicationAdapter`, `RedisRateLimitAdapter`
- Depends on: `StringRedisTemplate`, `DefaultRedisScript<Long>`
- Used by: `NotificationOrchestrator` (dedup direct), `RateLimitService` (rate limit)

**Persistence Layer:**
- Purpose: JPA entities, repositories, projections, attribute converters
- Location: `src/main/java/com/pheme/phemenotify/persistence/`
- Contains: `Notification`, `UserPreferences`, `FailedNotification`, `Channel`, `EventType`, `EventTypeRegistry`, repositories, `DeliveryStatsProjection`, attribute converters
- Depends on: PostgreSQL via Spring Data JPA
- Used by: Service layer, Messaging layer

**Config Layer:**
- Purpose: Spring configuration, `@ConfigurationProperties` beans, validation
- Location: `src/main/java/com/pheme/phemenotify/config/`
- Contains: `KafkaConfig`, `RedisConfig`, `ResilienceConfig`, `JpaConfig`, `JacksonConfig`, `ThymeleafConfig`, `RateLimitProperties`, `ResilienceProperties`, `RetrySchedulerProperties`, `validation/`
- Depends on: Nothing else in the project
- Used by: All layers via Spring injection

## Data Flow

### Primary Request Path (Kafka Event → Delivery)

1. Kafka message arrives on `notification.events` topic — `NotificationEventConsumer.handleEvent()` (`messaging/consumer/NotificationEventConsumer.java:37`)
2. `NotificationOrchestrator.process()` is called (`service/NotificationOrchestrator.java:50`)
3. Deduplication check: `RedisDeduplicationAdapter.isNew(event.id)` — SETNX `dedup:{id}` TTL=24h (`infrastructure/redis/RedisDeduplicationAdapter.java:19`)
4. User preferences loaded from `UserPreferenceRepository.findByUserId()` (`persistence/repository/UserPreferenceRepository.java`)
5. For each enabled channel: `processChannel()` (`service/NotificationOrchestrator.java:80`)
   - Persist `Notification` with status=PENDING (`persistence/entity/Notification.java`)
   - Rate limit check via `RateLimitService.checkLimit()` → Redis Lua sliding window (`infrastructure/redis/RedisRateLimitAdapter.java:22`)
   - Template rendering via `TemplateService.render()` → Thymeleaf (`service/TemplateService.java:22`)
   - Provider dispatch via `ProviderRegistry.getProvider(channel).send()` (`provider/ProviderRegistry.java:21`)
   - Update `Notification` status to DELIVERED or FAILED in DB

### DLT Path (Exhausted Retries)

1. After 3 Kafka retries, `@DltHandler` fires — `NotificationEventConsumer.handleDlt()` (`messaging/consumer/NotificationEventConsumer.java:45`)
2. `FailedNotification` entity saved to `failed_notifications` table with full event payload as JSONB

### Scheduled Retry Path

1. `FailedNotificationRetryScheduler.retryFailedNotifications()` runs on configured fixed-delay (`messaging/retry/FailedNotificationRetryScheduler.java:28`)
2. Fetches PENDING `FailedNotification` records where `next_retry_at < now`
3. Reconstructs `NotificationEvent` from stored JSONB payload
4. Calls `NotificationOrchestrator.processRetry()` — validates channel still enabled, then runs `processChannel()`
5. On success: marks `FailedNotification` as DELIVERED; on failure: increments retry count, applies exponential backoff or marks FAILED if exhausted

### REST Status Query Path

1. `GET /api/v1/notifications/{id}/status` → `NotificationController.getStatus()` (`api/controller/NotificationController.java:21`)
2. `NotificationService.getById()` reads from `NotificationRepository` (`service/NotificationService.java:17`)
3. Maps `Notification` entity to `NotificationResponse` DTO

**State Management:**
- Notification status transitions: `PENDING → DELIVERED` or `PENDING → FAILED`
- Status stored as PostgreSQL native ENUM `notification_status` via `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`
- No in-process state; all state lives in PostgreSQL and Redis

## Key Abstractions

**`NotificationProvider` (Strategy interface):**
- Purpose: Defines the contract for channel delivery; decouples orchestrator from delivery mechanism
- File: `provider/NotificationProvider.java`
- Pattern: Strategy — `ProviderRegistry` maps `Channel` enum to concrete implementation
- Implementations: `EmailProvider` (real, circuit-broken), `SmsProvider` (mock), `PushProvider` (mock)

**`EventType` (Registry interface):**
- Purpose: Open/closed extensibility for event types — add a new `@Component` implementing `EventType`, it auto-registers in `EventTypeRegistry`
- Files: `persistence/entity/EventType.java`, `persistence/entity/EventTypeRegistry.java`
- Pattern: Registry — `EventTypeRegistry` collects all `EventType` beans at startup via constructor injection `List<EventType>`
- Used by: `EventTypeAttributeConverter` (DB ↔ Java), `EventTypeDeserializer` (Kafka JSON ↔ Java)

**`DeliveryStatsProjection` (JPA interface projection):**
- Purpose: Typed result for native SQL window-function analytics query
- File: `persistence/projection/DeliveryStatsProjection.java`
- Pattern: Projection — Spring Data JPA proxies interface methods to SQL column aliases
- Query: `NotificationRepository.findDeliveryStats()` — CTE + `AVG() OVER()` 7-day rolling average

**`NotificationEvent` (Kafka message record):**
- Purpose: Immutable value object crossing the Kafka boundary
- File: `messaging/event/NotificationEvent.java`
- Pattern: Record — deserialized from JSON by Jackson using `EventTypeDeserializer` for the `EventType` field

## Entry Points

**Kafka Consumer:**
- Location: `messaging/consumer/NotificationEventConsumer.java`
- Triggers: Messages on `notification.events` topic (group `notification-hub`)
- Responsibilities: @RetryableTopic (3 attempts, exponential backoff 5s×2), @DltHandler persists to `failed_notifications`

**REST API:**
- Location: `api/controller/NotificationController.java`, `api/controller/PreferenceController.java`
- Triggers: HTTP requests
- Base path constant: `api/ApiPaths.java` (`/api/v1`)
- Responsibilities: Input validation (@Valid), DTO mapping, delegates to services

**Scheduled Retry:**
- Location: `messaging/retry/FailedNotificationRetryScheduler.java`
- Triggers: Fixed-delay timer (configured via `notification.retry-scheduler.fixed-delay`)
- Responsibilities: Polls `failed_notifications`, applies exponential backoff, delegates to `NotificationOrchestrator.processRetry()`

**Application Bootstrap:**
- Location: `PhemeNotifyApplication.java`
- Annotations: `@SpringBootApplication`, `@ConfigurationPropertiesScan`, `@EnableScheduling`

## Architectural Constraints

- **Threading:** Single-threaded Kafka consumer per partition; no async processing in the notification pipeline — each event is processed synchronously within the consumer thread
- **Global state:** `EventTypeRegistry` is a singleton holding a `Map<String, EventType>`; `ProviderRegistry` is a singleton holding a `Map<Channel, NotificationProvider>` — both are immutable after construction
- **Circular imports:** None detected
- **Partial failure contract:** `NotificationOrchestrator.processChannel()` catches all exceptions per channel; the orchestrator never throws to the consumer. One channel failure is isolated from others.
- **DB write before send:** `Notification` is persisted as PENDING before any send attempt, then updated. This creates an audit trail even if the JVM crashes mid-send.
- **Idempotency:** DB-level `UNIQUE` constraint on `idempotency_key` (`{eventId}:{channel}`) acts as secondary dedup guard after Redis dedup

## Anti-Patterns

### Missing `AnalyticsController` and `AnalyticsService`

**What happens:** `DeliveryStatsProjection` and the native analytics query exist in `NotificationRepository`, but no controller or service exposes `GET /api/v1/analytics/delivery-stats`. `StatsRequest` and `DeliveryStatsResponse` DTOs are present but unused.
**Why it's wrong:** Hard #3 (analytics endpoint) is structurally incomplete — the REST surface is missing, and Redis caching for analytics results is not implemented.
**Do this instead:** Implement `AnalyticsService` at `service/AnalyticsService.java` that calls `NotificationRepository.findDeliveryStats()` and caches results in Redis (key `analytics:{hash}`, TTL=3600s). Wire it through `AnalyticsController` at `api/controller/AnalyticsController.java`.

### `CircuitBreakerDecorator` in `infrastructure/resilience/` is absent

**What happens:** `infrastructure/resilience/` directory exists in the package plan (CLAUDE.md §4) but contains no Java files. Circuit breaker logic is embedded directly in `EmailProvider`.
**Why it's wrong:** The infrastructure layer's resilience package is a planned abstraction that was not materialized. No `NotificationMetrics` / `infrastructure/metrics/` exists either.
**Do this instead:** When adding metrics, create `infrastructure/metrics/NotificationMetrics.java` with Micrometer `Counter` + `Timer` beans. Keep CB logic in `EmailProvider` for now (it is acceptable there given the single email provider).

### `DeduplicationService` wrapper is absent — adapter called directly from orchestrator

**What happens:** `NotificationOrchestrator` injects `RedisDeduplicationAdapter` directly, bypassing the planned `DeduplicationService` wrapper layer.
**Why it's wrong:** Minor layering inconsistency — other infrastructure adapters are wrapped in service-layer classes (`RateLimitService` wraps `RedisRateLimitAdapter`), but deduplication is not.
**Do this instead:** Create `service/DeduplicationService.java` that delegates to `RedisDeduplicationAdapter`, and update `NotificationOrchestrator` to inject `DeduplicationService` instead.

## Error Handling

**Strategy:** Fail-safe per channel with normalized error codes stored in DB; RFC 9457 Problem Detail for REST errors.

**Patterns:**
- `NotificationOrchestrator.processChannel()`: try/catch per channel; sets `notification.errorMessage` to normalized string (`"RATE_LIMIT_EXCEEDED"`, `"SEND_FAILED:RuntimeException"`), never raw `e.getMessage()`
- `GlobalExceptionHandler extends ResponseEntityExceptionHandler`: handles `ResourceNotFoundException` (404), `RateLimitExceededException` (429), validation failures (400), and fallback (500) — all as `ProblemDetail`
- Stack traces logged via SLF4J (passing `e` as last arg) but never returned to client
- `FailedNotificationRetryScheduler`: on retry exhaustion sets `FailedNotification.status = FAILED`, logs error

## Cross-Cutting Concerns

**Logging:** SLF4J + Lombok `@Slf4j`; format `{action} {subject} {context}` with named parameters; `warn` for expected skips (duplicate, rate limit), `error` for unexpected failures with exception as last arg
**Validation:** `@Valid` + `@Validated` on request DTOs and `@ConfigurationProperties` classes; `PositiveDuration` custom constraint in `config/validation/`
**Serialization:** `JacksonConfig` registers `EventTypeDeserializer` (a Spring-managed `@Component` with `EventTypeRegistry` injected) via `SimpleModule` on the `ObjectMapper` bean
**JPA Auditing:** `@EnableJpaAuditing` in `JpaConfig`; `@CreatedDate`/`@LastModifiedDate` on all entities via `@EntityListeners(AuditingEntityListener.class)`
**Configuration properties:** All externalized config via `@ConfigurationProperties` + constructor binding + `@Validated`; registered via `@ConfigurationPropertiesScan` on main class — never `@Component` on properties classes

---

*Architecture analysis: 2026-05-24*
