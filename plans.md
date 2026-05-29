# Plans — pheme-notify

## [TODO] Normalize logs across the project

**Goal:** All log messages follow the rule from CLAUDE.md section 13.

**Format:** `{action} {subject} {context}` — past tense verb, named SLF4J `{}` params, always include `userId`/`eventId`.

**Files to audit:**
- `NotificationOrchestrator.java` — check warn/error messages have eventId + channel
- `EmailProvider.java` — verify `log.error` passes `throwable` as last arg
- `SmsProvider.java` — check format
- `RedisRateLimitAdapter.java` — check format
- `RedisDeduplicationAdapter.java` — check format
- `NotificationEventConsumer.java` — check DLT handler log
- `FailedNotificationRetryScheduler.java` — check retry logs

**Rules to enforce (see CLAUDE.md §13):**
1. `log.error` always has `e` as last argument (for stacktrace)
2. Every log line has at least one identifier (`userId`, `eventId`, `channel`)
3. `warn` = expected skip, `error` = unexpected failure
4. No `e.getMessage()` stored in DB — only in logs
5. No string concatenation in log args — use `{}` placeholders

---

## [TODO] Move template extension mapping into Channel enum

**File:** `src/main/java/com/pheme/phemenotify/service/TemplateService.java`  
**Issue:** Extension selection is hardcoded with EMAIL vs "everything else" logic:
```java
String extension = channel == Channel.EMAIL ? "html" : "txt";
```
If a new channel is added (e.g., PUSH with a different format), this silently produces wrong paths.

**Fix:** Add `getTemplateExtension()` method to `Channel` enum so each channel owns its extension:
```java
public enum Channel {
    EMAIL("html"),
    SMS("txt"),
    PUSH("txt");

    private final String templateExtension;

    Channel(String templateExtension) {
        this.templateExtension = templateExtension;
    }

    public String getTemplateExtension() {
        return templateExtension;
    }
}
```

Then in `TemplateService.resolveTemplateName()`:
```java
String extension = channel.getTemplateExtension();
```

**Impact:**
- `Channel.java` — add field + constructor + getter
- `TemplateService.resolveTemplateName()` — replace ternary with `channel.getTemplateExtension()`
- `TemplateServiceTest` — no changes needed (tests already pass channel explicitly)

---

## [FUTURE] pheme-analytics — extract as separate microservice

**When to do it:** when analytics SQL queries start slowing down the main notification pipeline,
or when the team grows and analytics needs independent deploy cycle.

**Why analytics is a good candidate to extract:**
- Read-only — reads from the same PostgreSQL, no distributed transactions needed
- Independent scaling — heavy SQL queries don't affect Kafka consumer throughput
- Clear boundary — no shared state with the main pipeline
- Can be rewritten in Python/Pandas later if needed

### Architecture

```
pheme-notify (monolith)          pheme-analytics (new service)
  Kafka consumer              →   reads same PostgreSQL (notifications table)
  rate limit                      GET /api/v1/analytics/delivery-stats
  email/sms pipeline              Redis cache TTL=1h
  writes to notifications         exposes data to pheme-ui dashboard
```

### What moves to pheme-analytics

| Component | From pheme-notify | To pheme-analytics |
|-----------|------------------|--------------------|
| `AnalyticsService` | move | own service layer |
| `AnalyticsController` | move | own REST controller |
| `NotificationRepository` (analytics query only) | copy/share | own read-only repository |
| `DeliveryStatsProjection` | move | own projection |
| Redis cache for analytics | shared Redis | same Redis, same key format |

### What stays in pheme-notify

- `NotificationRepository` — write operations + `findByIdempotencyKey`
- All Kafka pipeline code
- Rate limiting, dedup
- Preference API

### Tech stack for pheme-analytics

```
Spring Boot (same version)
Spring Data JPA — read-only connection to same PostgreSQL
Spring Data Redis — cache TTL=1h
springdoc-openapi — Swagger
No Kafka dependency
```

### Trigger conditions — extract when ANY of these is true

1. Analytics queries take >500ms and affect Kafka consumer latency
2. Team splits — separate person owns analytics feature
3. Analytics needs different tech (Python, ClickHouse, etc.)
4. Deploy frequency differs — analytics changes daily, pipeline is stable

### Steps to extract

1. Finish `AnalyticsService` + `AnalyticsController` in pheme-notify first (Sprint 4)
2. Create new Spring Boot project `pheme-analytics`
3. Move analytics code — no logic changes, just new project
4. Configure read-only datasource pointing to same PostgreSQL
5. Update pheme-ui to call pheme-analytics instead of pheme-notify for stats
6. Add pheme-analytics to `docker-compose.yml` (port 8082)
