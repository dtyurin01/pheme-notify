# External Integrations

**Analysis Date:** 2026-05-24

## APIs & External Services

**Message Broker:**
- Apache Kafka (via Confluent) — Event-driven notification pipeline
  - SDK/Client: `spring-boot-starter-kafka` (Spring Kafka wrapping Apache Kafka client)
  - Mode: KRaft (no ZooKeeper), image `confluentinc/cp-kafka:7.9.0`
  - Consumer: `messaging/consumer/NotificationEventConsumer.java` — `@KafkaListener(topics = "notification.events")`
  - Retry: `@RetryableTopic(attempts="3", backOff=@BackOff(delay=5000, multiplier=2))` — auto-creates retry topics `notification.events-retry-0`, `notification.events-retry-1`
  - DLT: `notification.events.dlt` — `@DltHandler` persists to `failed_notifications`
  - Topics created explicitly via `@Bean NewTopic` in `config/KafkaConfig.java` (`KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"`)
  - Connection env var: `KAFKA_BOOTSTRAP_SERVERS` (default: `localhost:9092`)

**Email (Development):**
- Mailpit — SMTP dev mail interceptor; replaces MailHog
  - SDK/Client: `spring-boot-starter-mail` → `JavaMailSender` (auto-configured)
  - Implementation: `provider/EmailProvider.java` — `MimeMessage` + `MimeMessageHelper`, HTML support
  - Dev endpoint: `localhost:1025` (SMTP), `http://localhost:8025` (Web UI)
  - Auth: none (`mail.smtp.auth: false`, `starttls: false`)
  - Connection env vars: `MAIL_HOST` (default: `localhost`), `MAIL_PORT` (default: `1025`)
  - Circuit Breaker: `io.github.resilience4j.circuitbreaker.CircuitBreaker` wraps all sends; config in `config/ResilienceConfig.java`

**Monitoring:**
- Prometheus — Metrics scraping
  - Endpoint: `/actuator/prometheus` (exposed via `management.endpoints.web.exposure.include`)
  - Scrape target: `host.docker.internal:8080` (see `infra/prometheus.yml`)
  - Client: `io.micrometer:micrometer-registry-prometheus` (runtime dependency)
- Grafana — Metrics visualization
  - Datasource: Prometheus (`http://prometheus:9090`)
  - Dev URL: `http://localhost:3000`
  - Auth env vars: `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD`

**Observability UI:**
- Kafka UI (`provectuslabs/kafka-ui:latest`) — Dev Kafka topic browser at `http://localhost:8090`

## Data Storage

**Databases:**
- PostgreSQL 17 — Primary relational store
  - Connection env vars: `DB_HOST` (default: `localhost`), `DB_PORT` (default: `5432`), `DB_NAME` (default: `notification_hub`), `DB_USER`, `DB_PASSWORD`
  - JDBC URL pattern: `jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}`
  - Client/ORM: Hibernate 6.x via `spring-boot-starter-data-jpa`, dialect `org.hibernate.dialect.PostgreSQLDialect`
  - Docker port: `55200:5432` (host:container)
  - Volume: `pg_data` (persistent)
  - Schema managed by Flyway (see Flyway section)

**Cache:**
- Redis 7 (Alpine) — Deduplication + Rate limiting + Analytics cache
  - Connection env vars: `REDIS_HOST` (default: `localhost`), `REDIS_PORT` (default: `6379`)
  - Client: Spring Data Redis / Lettuce (via `spring-boot-starter-data-redis`)
  - Persistence: AOF enabled (`appendonly yes --appendfsync everysec`)
  - Volume: `redis_data` (persistent)
  - Beans: `RedisTemplate<String, String>`, `StringRedisTemplate`, `DefaultRedisScript<Long>` in `config/RedisConfig.java`
  - Key patterns:
    - Deduplication: `dedup:{event.id}` — TTL 24h (`infrastructure/redis/RedisDeduplicationAdapter.java`)
    - Rate limit: `ratelimit:{userId}:{channel}` — sliding window sorted set, Lua script (`infrastructure/redis/RedisRateLimitAdapter.java`)
    - Analytics cache: `analytics:{hash(params)}` — TTL 3600s (managed manually in `service/AnalyticsService.java`, not `@Cacheable`)
  - Lua script: `src/main/resources/redis/rate_limit.lua` — atomic sliding window counter loaded via `DefaultRedisScript`

**File Storage:**
- Local filesystem only — Thymeleaf templates stored as classpath resources (`src/main/resources/templates/`)
  - Email templates: `templates/email/order-completed.html`, `templates/email/` (HTML)
  - SMS templates: `templates/sms/user-registered.txt` (plain text)

## Authentication & Identity

**Auth Provider:**
- None (not implemented) — No authentication/authorization layer present
- User identity propagated as plain `userId` string in `NotificationEvent` and `UserPreferences`

**Email Header Security:**
- `EmailProvider` validates email format and defends against header injection (CR/LF checks) — see `security-issues.md`

## Schema Management

**Flyway:**
- Enabled: `spring.flyway.enabled: true`
- Locations: `classpath:db/migration`
- Migrations (applied in order):
  - `V1__create_enums.sql` — PostgreSQL native ENUMs: `notification_status`, `notification_channel`
  - `V2__create_user_preferences.sql` — `user_preferences` table
  - `V3__create_notifications.sql` — `notifications` table with indexes (analytics query target)
  - `V4__create_failed_notifications.sql` — `failed_notifications` table with JSONB `event_payload`
  - `V5__create_user_preference_channels.sql` — `user_preference_channels` join table
- JPA DDL: `ddl-auto: validate` — schema validated against Flyway output, never generated

## Monitoring & Observability

**Metrics (Micrometer):**
- Custom counters/timers defined in `infrastructure/metrics/NotificationMetrics.java`:
  - `Counter: notifications.sent` — tagged by `channel`
  - `Counter: notifications.failed` — tagged by `channel`
  - `Timer: notification.send.duration` — tagged by `channel`
- Actuator endpoints exposed: `health`, `info`, `prometheus`, `metrics`
- Health details always shown (`show-details: always`)
- Health indicators expected: PostgreSQL, Redis, Kafka (via auto-configured Spring Boot health contributors)

**Logging:**
- Framework: SLF4J + Logback (Spring Boot default)
- Log levels in `application.yaml`:
  - `com.pheme.phemenotify: DEBUG`
  - `org.springframework.kafka: INFO`
  - `org.hibernate.SQL: DEBUG`
- Pattern: `{action} {subject} {context}` with named SLF4J `{}` parameters (see CLAUDE.md §13)

## CI/CD & Deployment

**Hosting:**
- Docker Compose for local/dev — `docker-compose.yml`
- Application container: Dockerfile not yet created; app service commented out in `docker-compose.yml`
- Target profile for containerized run: `SPRING_PROFILES_ACTIVE=docker`

**CI Pipeline:**
- Not detected — no `.github/workflows/`, `.gitlab-ci.yml`, or equivalent CI config present

## Webhooks & Callbacks

**Incoming:**
- Kafka topic `notification.events` — primary event ingestion point (not HTTP webhook; Kafka consumer)

**Outgoing:**
- None — service is a consumer/processor only; no outbound HTTP webhooks configured

## Environment Configuration Summary

**Required environment variables (no safe defaults):**
- `DB_PASSWORD` — PostgreSQL password
- `KAFKA_CLUSTER_ID` — Kafka KRaft cluster ID
- `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD` — Grafana credentials

**Variables with safe dev defaults (override for production):**
- `DB_HOST` (localhost), `DB_PORT` (5432), `DB_NAME` (notification_hub), `DB_USER` (pheme)
- `REDIS_HOST` (localhost), `REDIS_PORT` (6379)
- `KAFKA_BOOTSTRAP_SERVERS` (localhost:9092)
- `MAIL_HOST` (localhost), `MAIL_PORT` (1025)
- `SERVER_PORT` (8080)

**Secrets location:**
- `.env` file at project root (gitignored); template at `.env.example`
- Spring Boot reads via `spring.config.import: optional:file:.env[.properties]`

---

*Integration audit: 2026-05-24*
