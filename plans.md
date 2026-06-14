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

---

## Service-to-service auth (OAuth2 client_credentials)

### Problem

`PreferenceController` and `NotificationController` are fully open — any caller
can read/overwrite preferences for any `userId`. Pheme-notify doesn't own user
identity (see CONCERNS.md), so per-user authz is out of scope. What's needed is
**service-to-service authentication**: only trusted backends (e.g. shop-backend)
may call this API at all. Authorization for a specific `userId` remains the
calling service's responsibility.

### Target flow

```
shop-backend --(client_credentials: client_id+secret)--> Auth Server (Keycloak)
shop-backend --(Authorization: Bearer <access_token>)--> pheme-notify
pheme-notify --(validate token via JWKS)--> Auth Server
```

pheme-notify becomes an OAuth2 **resource server** only — it never issues
tokens, only validates them.

### Resources needed

- **Auth Server**: Keycloak (self-hosted, Docker container) — realistic for a
  portfolio, free, runs locally via `docker-compose`. Alternative: Auth0 (managed,
  but adds external dependency/account requirement — Keycloak preferred for
  local dev parity).
- New dependency: `spring-boot-starter-oauth2-resource-server`
- Keycloak realm config: one `confidential` client per consuming backend
  (e.g. `shop-backend`), client_id + client_secret stored in `.env`
- `docker-compose.yml`: new `keycloak` service (port 8180), Postgres schema or
  separate DB for Keycloak's own state

### Sprint breakdown

**Sprint A — Keycloak infra**
1. Add `keycloak` service to `docker-compose.yml` (dev profile), with its own
   Postgres DB or `dev-mem` for local-only use
2. Create realm `pheme`, create client `shop-backend` (confidential,
   `client_credentials` grant only)
3. Document how to obtain a token manually (curl against
   `/realms/pheme/protocol/openid-connect/token`) — add to README
4. Add `KEYCLOAK_ISSUER_URI`, client id/secret to `.env.example`

**Sprint B — Resource server wiring**
5. Add `spring-boot-starter-oauth2-resource-server` to `pom.xml`
6. `SecurityConfig` — `@EnableWebSecurity`, configure
   `oauth2ResourceServer().jwt()` with `issuer-uri` from Keycloak
7. Permit `/actuator/health`, `/swagger-ui/**`, `/v3/api-docs/**` without auth;
   require valid JWT for `/api/v1/**`
8. Update `GlobalExceptionHandler` if needed for 401/403 ProblemDetail shape
   (Spring Security default may not match RFC 9457 — verify and add handler)

**Sprint C — Tests + docs**
9. Integration tests: `@WebMvcTest` with `@WithMockUser`/mocked JWT for 200 path;
   no-token / invalid-token → 401 ProblemDetail
10. Update README "Event contract / Integration" section: how shop-backend
    fetches a token and calls the API with `Authorization: Bearer`
11. Update CONCERNS.md — mark "No authentication" as resolved, link to this
    section

### Open questions / not in scope

- Per-`userId` authorization (does this client own this userId?) — explicitly
  NOT handled here; remains caller's responsibility per the trust model above
- Token caching/refresh on the client side — document as client's concern,
  not pheme-notify's
- mTLS — alternative considered, dropped in favor of OAuth2 as more standard
  and easier to demo/test locally
