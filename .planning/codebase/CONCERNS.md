# Codebase Concerns

**Analysis Date:** 2026-05-24

## Tech Debt

**DeduplicationService is a dead layer:**
- Issue: `DeduplicationService` (`src/main/java/com/pheme/phemenotify/service/DeduplicationService.java`) exists as a service-layer wrapper but is never injected anywhere. `NotificationOrchestrator` calls `RedisDeduplicationAdapter.isNew()` directly, bypassing the service.
- Files: `src/main/java/com/pheme/phemenotify/service/DeduplicationService.java`, `src/main/java/com/pheme/phemenotify/service/NotificationOrchestrator.java` (line 51)
- Impact: Dead code creates confusion — other developers will wonder which path to use. Architecture breaks the intended `Service → Adapter` layering.
- Fix approach: Inject `DeduplicationService` into `NotificationOrchestrator` instead of `RedisDeduplicationAdapter`, or delete `DeduplicationService` and document the direct-adapter pattern as intentional.

**Deduplication TTL is hardcoded:**
- Issue: `RedisDeduplicationAdapter` has `private static final Duration TTL = Duration.ofHours(24); // make config?` — the comment itself flags this as known debt.
- Files: `src/main/java/com/pheme/phemenotify/infrastructure/redis/RedisDeduplicationAdapter.java` (line 15)
- Impact: Cannot tune dedup window without code change and redeploy.
- Fix approach: Add `pheme.deduplication.ttl` to `application.yaml` and a `DeduplicationProperties` config class (following the existing `@ConfigurationProperties` pattern).

**TemplateService has open-coded channel→extension mapping:**
- Issue: `resolveTemplateName()` uses `channel == Channel.EMAIL ? "html" : "txt"` — any new channel (e.g., `PUSH` with a different format) silently produces wrong template paths without a compile error or runtime warning until a template send fails.
- Files: `src/main/java/com/pheme/phemenotify/service/TemplateService.java` (line 43)
- Impact: Adding a new channel without updating this ternary causes `TemplateNotFoundException` at runtime, not at compile time. The fix is already fully designed in `plans.md`.
- Fix approach: Move extension to `Channel` enum as `getTemplateExtension()` method (see `plans.md` for exact code).

**RateLimitProperties uses setter binding instead of constructor binding:**
- Issue: `RateLimitProperties` has explicit `setEmail/setSms/setPush` methods, mixing setter binding (for top-level fields) with the `record ChannelLimit` pattern (constructor binding for nested fields). The CLAUDE.md convention requires constructor binding with `final` fields.
- Files: `src/main/java/com/pheme/phemenotify/config/RateLimitProperties.java` (lines 30–32)
- Impact: Mutable top-level fields can be modified after construction; inconsistent with rest of config layer.
- Fix approach: Refactor to full constructor binding — `final` fields for `email`, `sms`, `push` with a single constructor.

**`open-in-view` is not disabled:**
- Issue: `spring.jpa.open-in-view: false` is noted in CLAUDE.md section 17 as a known missing config, but it is not present in `src/main/resources/application.yaml`.
- Files: `src/main/resources/application.yaml`
- Impact: Hibernate session stays open across the full HTTP request lifecycle, risking lazy-load side effects and connection pool exhaustion under load.
- Fix approach: Add `spring.jpa.open-in-view: false` to `application.yaml`.

**`EventTypeAttributeConverter` is both `@Converter` and `@Component`:**
- Issue: The converter is annotated with `@Converter(autoApply=true)` and `@Component`. Spring manages it as a bean (for `EventTypeRegistry` injection), but JPA also auto-applies it. This is a known workaround for injecting Spring beans into JPA converters.
- Files: `src/main/java/com/pheme/phemenotify/persistence/converter/EventTypeAttributeConverter.java`
- Impact: Fragile — depends on Spring + Hibernate integration behaviour. If JPA creates its own instance (non-Spring-managed), `EventTypeRegistry` will be `null` and all DB reads will throw `NullPointerException`.
- Fix approach: Document the dependency explicitly; add a null-guard on `registry` in `convertToEntityAttribute` with a clear error message indicating misconfiguration.

---

## Known Bugs

**`shouldMarkAsFailed_whenProviderThrows` test asserts wrong `errorMessage`:**
- Symptoms: Test at `NotificationOrchestratorTest.java` line 121 asserts `errorMessage` equals `"SMTP error"` (the raw exception message). Production code at `NotificationOrchestrator.java` line 119 stores `"SEND_FAILED:" + e.getClass().getSimpleName()`. If the exception class is `RuntimeException`, the stored value is `"SEND_FAILED:RuntimeException"`, not `"SMTP error"`.
- Files: `src/test/java/com/pheme/phemenotify/service/NotificationOrchestratorTest.java` (line 121), `src/main/java/com/pheme/phemenotify/service/NotificationOrchestrator.java` (line 119)
- Trigger: Running `NotificationOrchestratorTest#shouldMarkAsFailed_whenProviderThrows`
- Workaround: None — the test currently passes only if this assertion is wrong or the production code was changed without updating the test expectation.

**`shouldMarkAsFailed_whenRateLimitExceeded` asserts raw exception message is stored in DB:**
- Symptoms: Test at `NotificationOrchestratorTest.java` line 143 asserts `errorMessage` contains `"max 5 email notifications per 1h"`. Production code stores the fixed string `"RATE_LIMIT_EXCEEDED"` (line 99 of `NotificationOrchestrator`). The test assertion contradicts the implementation.
- Files: `src/test/java/com/pheme/phemenotify/service/NotificationOrchestratorTest.java` (line 143), `src/main/java/com/pheme/phemenotify/service/NotificationOrchestrator.java` (line 99)
- Trigger: Running `NotificationOrchestratorTest#shouldMarkAsFailed_whenRateLimitExceeded`
- Workaround: None — one of the two (test or implementation) is incorrect.

**`FailedNotificationRetryScheduler` uses `@Scheduled(fixedDelayString)` referencing a property path, but `RetrySchedulerProperties.fixedDelay` is typed `Duration` — Spring `@Scheduled` requires ISO-8601 string:**
- Symptoms: The `fixedDelayString = "${notification.retry-scheduler.fixed-delay}"` annotation reads the raw YAML value `PT1M` (ISO-8601). This works as long as the YAML value stays ISO-8601. If changed to Spring shorthand (`1m`), `@Scheduled` will fail at runtime with a parse error.
- Files: `src/main/java/com/pheme/phemenotify/messaging/retry/FailedNotificationRetryScheduler.java` (line 27), `src/main/resources/application.yaml` (line 105)
- Trigger: Changing `fixed-delay: PT1M` to `fixed-delay: 1m` in YAML.
- Workaround: Keep value as ISO-8601 (`PT1M`) in YAML — documented in CLAUDE.md section 14. The risk is that future editors familiar with Spring shorthand will not know this constraint.

---

## Security Considerations

**`IllegalArgumentException` from `EmailProvider` leaks `userId` in exception message:**
- Risk: `EmailProvider` throws `new IllegalArgumentException("Email not found in payload for user: " + event.userId())` (line 32). If this exception propagates through `NotificationOrchestrator.processChannel` and up to `GlobalExceptionHandler.exception()`, the `errorId` is logged but the handler returns a generic message — so no leak via HTTP. However the exception message is logged at `log.error` level in the orchestrator, meaning `userId` appears in application logs which may be forwarded to external log aggregators.
- Files: `src/main/java/com/pheme/phemenotify/provider/EmailProvider.java` (line 32)
- Current mitigation: `GlobalExceptionHandler` does not expose exception details in the HTTP response body.
- Recommendations: Remove `userId` from the exception message; log it separately via SLF4J structured params: `log.warn("Email missing in payload for user {}", event.userId())`.

**No authentication/authorization on REST endpoints:**
- Risk: `PreferenceController` (`GET/PUT /api/v1/users/{userId}/preferences`) and `NotificationController` (`GET /api/v1/notifications/{id}/status`) are fully open — any caller can read or overwrite any user's preferences using any `userId` path variable.
- Files: `src/main/java/com/pheme/phemenotify/api/controller/PreferenceController.java`, `src/main/java/com/pheme/phemenotify/api/controller/NotificationController.java`
- Current mitigation: None. Project has no `SecurityConfig` class.
- Recommendations: This is a portfolio project — document the gap explicitly. For production: add Spring Security with JWT validation; `userId` in path must match authenticated principal.

**Actuator endpoints are fully exposed without auth:**
- Risk: `management.endpoints.web.exposure.include: health,info,prometheus,metrics` is configured in `application.yaml` (line 72). The `prometheus` scrape endpoint exposes internal metric names and tags.
- Files: `src/main/resources/application.yaml` (line 72)
- Current mitigation: None.
- Recommendations: In production, restrict actuator endpoints to internal network or add HTTP Basic auth via `spring.security.user.*`.

---

## Performance Bottlenecks

**Analytics query has no dedicated database connection pool tuning:**
- Problem: The rolling 7-day average query in `NotificationRepository.findDeliveryStats()` is a multi-CTE window function that performs a full table scan of `notifications` for the given date range. Under high insert volume (many Kafka events), this query competes with write traffic on the same connection pool.
- Files: `src/main/java/com/pheme/phemenotify/persistence/repository/NotificationRepository.java` (lines 17–37)
- Cause: Single datasource, no read replica, default HikariCP pool (10 connections).
- Improvement path: Redis cache with TTL=1h is planned in `AnalyticsService` (Sprint 4) and will hide the query for repeated calls. Long-term: read replica or materialized view.

**`UserPreferences.enabledChannels` uses `FetchType.EAGER`:**
- Problem: `@ElementCollection(fetch = FetchType.EAGER)` on `enabledChannels` means every `findByUserId` call issues two SQL queries (preferences + channels join). Under Kafka burst traffic where `NotificationOrchestrator.process()` calls `userPreferenceRepository.findByUserId()` per event, this doubles DB round trips.
- Files: `src/main/java/com/pheme/phemenotify/persistence/entity/UserPreferences.java` (line 34)
- Cause: `@ElementCollection` defaults to `LAZY` but was set `EAGER` — likely to avoid `LazyInitializationException` in the absence of `open-in-view`. Once `open-in-view: false` is added, this will require a transaction boundary.
- Improvement path: Keep `EAGER` for now and add a preference cache (Redis TTL=5min) in `PreferenceService` to avoid repeated DB hits for the same user during a burst.

**`FailedNotificationRetryScheduler` loads all pending records into memory:**
- Problem: `failedNotificationRepository.findByStatusAndNextRetryAtBefore(...)` returns `List<FailedNotification>` with no pagination. If thousands of notifications accumulate in `PENDING` status (e.g., after an extended outage), the scheduler loads all of them into a single Java list.
- Files: `src/main/java/com/pheme/phemenotify/messaging/retry/FailedNotificationRetryScheduler.java` (line 30)
- Cause: No `Pageable` parameter in the repository query.
- Improvement path: Add `Pageable` to `findByStatusAndNextRetryAtBefore` and process in batches of 100. Partial index `idx_failed_notifications_scheduler` in V4 migration already limits scope but does not prevent large result sets.

---

## Fragile Areas

**`FailedNotificationRetryScheduler.toEvent()` uses unchecked cast and string parsing:**
- Files: `src/main/java/com/pheme/phemenotify/messaging/retry/FailedNotificationRetryScheduler.java` (lines 67–78)
- Why fragile: `@SuppressWarnings("unchecked")` cast `(Map<String, String>) raw.get("payload")` will throw `ClassCastException` at runtime if the payload stored in JSONB was deserialized as `Map<String, Object>` by Jackson. `Instant.parse((String) raw.get("occurredAt"))` throws `ClassCastException` if Jackson deserializes the value as a non-String. Both failures surface only at retry time, not at event ingestion time.
- Safe modification: Add explicit null checks and type guards for each cast. Consider a typed DTO or a typed `ObjectMapper.convertValue()` call instead of raw casts.
- Test coverage: `FailedNotificationRetrySchedulerTest` uses `FailedNotificationTestData` which likely provides a controlled `Map` — the cast bug will not appear in unit tests.

**`NotificationOrchestrator.processChannel` has a TOCTOU gap between Redis dedup and DB idempotency check:**
- Files: `src/main/java/com/pheme/phemenotify/service/NotificationOrchestrator.java` (lines 51–93)
- Why fragile: Redis `SETNX` is checked first (line 51). If two Kafka partitions deliver the same event concurrently, both can pass the Redis check in the same millisecond window. The `DataIntegrityViolationException` on `notificationRepository.save()` (line 90) acts as the final guard. However, catching `DataIntegrityViolationException` at the `processChannel` level means both goroutines proceed with template rendering and provider sends before the DB constraint fires on second save. The first one succeeds; the second one tries to send the notification, then catches the exception on save. Result: double delivery is possible in rare race conditions.
- Safe modification: Wrap `save` + `send` in a `@Transactional` method with `REQUIRES_NEW` propagation, or perform an upsert-style `findByIdempotencyKey` check inside the transaction before sending.
- Test coverage: No concurrency tests exist for this race condition.

**`ProviderRegistry` is hardwired to three concrete providers:**
- Files: `src/main/java/com/pheme/phemenotify/provider/ProviderRegistry.java` (lines 13–18)
- Why fragile: Constructor takes `EmailProvider`, `SmsProvider`, `PushProvider` explicitly. Adding a fourth channel requires modifying `ProviderRegistry` constructor and `Channel` enum. If a new `Channel` enum value is added without updating `ProviderRegistry`, the registry will throw `IllegalArgumentException` at runtime rather than a compile-time error.
- Safe modification: Inject `List<NotificationProvider>` with a `@Channel` qualifier or use a `Map<Channel, NotificationProvider>` assembled via `@Bean` with the registry auto-discovering providers. Currently the hardwiring is acceptable for 3 channels.

---

## Scaling Limits

**Kafka consumer group has single instance assumption:**
- Current capacity: Single application instance consuming `notification.events` with `groupId = notification-hub`. Topic has 3 partitions (from `KafkaConfig`).
- Limit: Redis deduplication (`SETNX`) is correct under horizontal scale. The DB idempotency key (`UNIQUE` constraint on `idempotency_key`) provides a second guard. However, `FailedNotificationRetryScheduler` runs on every instance — multiple instances will process the same `failed_notifications` rows concurrently with no distributed lock.
- Scaling path: Add `SELECT ... FOR UPDATE SKIP LOCKED` to the retry repository query to prevent multiple scheduler instances from processing the same row.

**Analytics query performance degrades linearly with table size:**
- Current capacity: Acceptable for portfolio demo with hundreds of rows.
- Limit: Without materialized views or partitioning, a `notifications` table with millions of rows will cause the analytics CTE to scan a large range even with the `created_at` index.
- Scaling path: Implement the planned `AnalyticsService` Redis cache (TTL=1h). Long-term: PostgreSQL table partitioning by `created_at` month.

---

## Dependencies at Risk

**No dependency pinning for `testcontainers-redis`:**
- Risk: `com.redis:testcontainers-redis:2.2.2` is a third-party Testcontainers module (not part of the official Testcontainers BOM). It may lag behind official Testcontainers releases or be abandoned.
- Impact: Test infrastructure breaks if module is removed from Maven Central or becomes incompatible with future Testcontainers versions.
- Migration plan: Monitor for official `org.testcontainers:redis` support (not yet available as of 2026-05). Fallback: replace with a `GenericContainer("redis:7-alpine")` and manual port mapping — requires minor test changes.

---

## Missing Critical Features

**`AnalyticsService` and `AnalyticsController` do not exist:**
- Problem: Sprint 4 requirement (Hard #3). The SQL query, projection, DTOs (`StatsRequest`, `DeliveryStatsResponse`, `DeliveryStatsProjection`), and repository method are all implemented. Only the service layer and controller are missing.
- Blocks: `GET /api/v1/analytics/delivery-stats` endpoint is completely non-functional. The `StatsRequest` and `DeliveryStatsResponse` DTOs exist with no callers.
- Files needing creation: `src/main/java/com/pheme/phemenotify/service/AnalyticsService.java`, `src/main/java/com/pheme/phemenotify/api/controller/AnalyticsController.java`

**`NotificationMetrics` and `CircuitBreakerDecorator` infrastructure stubs are empty directories:**
- Problem: `src/main/java/com/pheme/phemenotify/infrastructure/metrics/` and `src/main/java/com/pheme/phemenotify/infrastructure/resilience/` exist as directories but contain no files. CLAUDE.md section 9 and the production checklist require `notifications.sent`, `notifications.failed`, and `notification.send.duration` metrics.
- Blocks: Prometheus/Grafana dashboard cannot show notification metrics. Sprint 5 observability work cannot proceed without these components.
- Files needing creation: `src/main/java/com/pheme/phemenotify/infrastructure/metrics/NotificationMetrics.java`, `src/main/java/com/pheme/phemenotify/infrastructure/resilience/CircuitBreakerDecorator.java`

**`NotificationStatus.CANCELLED` is defined but never used:**
- Problem: `NotificationStatus` enum declares `CANCELLED` but no code path ever sets a notification to this status. The PostgreSQL enum in `V1__create_enums.sql` includes `CANCELLED`.
- Blocks: Nothing currently, but the enum value suggests planned cancellation functionality that was never implemented.
- Files: `src/main/java/com/pheme/phemenotify/persistence/entity/NotificationStatus.java`, `src/main/resources/db/migration/V1__create_enums.sql`

**`Notification.providerResponse` field is never populated:**
- Problem: `Notification` entity has a `providerResponse` TEXT field (line 50) and the corresponding DB column exists in `V3__create_notifications.sql` (line 9). No code in any provider or orchestrator ever calls `setProviderResponse()`.
- Blocks: Provider response tracking (useful for debugging failed deliveries) is dead schema.
- Files: `src/main/java/com/pheme/phemenotify/persistence/entity/Notification.java` (line 50), `src/main/java/com/pheme/phemenotify/service/NotificationOrchestrator.java`

---

## Test Coverage Gaps

**No integration test for `NotificationRepository.findDeliveryStats` (Hard #3 query):**
- What's not tested: The native SQL window function query with rolling 7-day average. `NotificationRepositoryTest` only tests `findById` and `findByIdempotencyKey`.
- Files: `src/test/java/com/pheme/phemenotify/persistence/repository/NotificationRepositoryTest.java`
- Risk: The CTE + window function works on PostgreSQL but any column name mismatch in `DeliveryStatsProjection` or a PostgreSQL version difference will surface only in production.
- Priority: High — this is the core Hard #3 deliverable.

**No integration test for Kafka consumer end-to-end (DLT, retry, dedup):**
- What's not tested: The `@RetryableTopic` + `@DltHandler` flow with a real Kafka container. `NotificationEventConsumerTest` is a pure unit test using Mockito.
- Files: `src/test/java/com/pheme/phemenotify/messaging/consumer/NotificationEventConsumerTest.java`
- Risk: The `@RetryableTopic` configuration, topic naming strategy, and DLT routing are untested against a real broker. A misconfigured suffix or consumer group ID would not be caught.
- Priority: High — zero message loss is a key project claim.

**No concurrency test for the dedup/idempotency TOCTOU race:**
- What's not tested: Concurrent processing of the same event ID by two threads.
- Files: No file — gap is in orchestrator logic at `src/main/java/com/pheme/phemenotify/service/NotificationOrchestrator.java`
- Risk: Rare double-delivery under partition rebalancing or replay.
- Priority: Medium — only observable under concurrent load.

**No test for `FailedNotificationRetryScheduler` JSON payload deserialization correctness:**
- What's not tested: Whether `toEvent()` correctly round-trips the `Map<String, Object>` from JSONB back to `NotificationEvent`, specifically the unchecked cast of `payload` and `Instant.parse`.
- Files: `src/test/java/com/pheme/phemenotify/messaging/retry/FailedNotificationRetrySchedulerTest.java`
- Risk: `ClassCastException` at retry time due to Jackson deserializing numeric values or nested objects differently than expected.
- Priority: Medium.

---

*Concerns audit: 2026-05-24*