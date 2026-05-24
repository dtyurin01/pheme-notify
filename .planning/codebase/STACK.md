# Technology Stack

**Analysis Date:** 2026-05-24

## Languages

**Primary:**
- Java 21 - All production and test code (`src/main/java/`, `src/test/java/`)

**Secondary:**
- Lua - Redis scripting for atomic rate limiting (`src/main/resources/redis/rate_limit.lua`)
- SQL - Native Flyway migrations and native JPA queries (`src/main/resources/db/migration/`)
- YAML - Application configuration (`src/main/resources/application.yaml`, `src/main/resources/application-docker.yml`)

## Runtime

**Environment:**
- Java 21 (LTS) — configured via `maven-compiler-plugin` `<release>21</release>`

**Package Manager:**
- Maven — `pom.xml`, wrapper present (`mvnw`, `mvnw.cmd`)
- Lockfile: Not applicable (Maven resolves from remote repos at build time; no lockfile equivalent)

## Frameworks

**Core:**
- Spring Boot 4.0.5 — Application bootstrap, auto-configuration, `@SpringBootApplication`
- Spring Web MVC — REST controllers (`spring-boot-starter-webmvc`), `api/controller/`
- Spring Data JPA — Repository layer (`spring-boot-starter-data-jpa`), `persistence/repository/`
- Spring Data Redis — Redis integration (`spring-boot-starter-data-redis`), Lettuce client
- Spring Kafka — Kafka consumer/producer (`spring-boot-starter-kafka`), `@KafkaListener`, `@RetryableTopic`
- Spring Mail — Email delivery via SMTP (`spring-boot-starter-mail`), `JavaMailSender`
- Spring Actuator — Health endpoints, metrics exposure (`spring-boot-starter-actuator`)
- Spring Validation — Bean Validation 3.x (`spring-boot-starter-validation`), `@Validated`, `@Min`
- Thymeleaf — Email/SMS template rendering (`spring-boot-starter-thymeleaf`), `src/main/resources/templates/`
- Spring Cloud Circuit Breaker / Resilience4j — Circuit breaking for providers (`spring-cloud-starter-circuitbreaker-resilience4j`)
- Spring Cloud 2025.1.1 — BOM managing cloud dependencies

**Testing:**
- JUnit 5 (Jupiter) — Test runner, bundled via `spring-boot-starter-test`
- Mockito — Mocking framework, bundled via `spring-boot-starter-test`
- Spring Boot Test — `@SpringBootTest`, `@WebMvcTest`, MockMvc (`spring-boot-starter-webmvc-test`)
- Spring Kafka Test — `spring-kafka-test`
- Testcontainers 1.21.4 — Containerized integration tests:
  - `testcontainers:junit-jupiter`
  - `testcontainers:kafka` — `confluentinc/cp-kafka:7.9.0`
  - `testcontainers:postgresql` — `postgres:17`
  - `com.redis:testcontainers-redis:2.2.2` — `redis:7-alpine`
- Spring Boot Testcontainers — `spring-boot-testcontainers` for `@DynamicPropertySource` integration

**Build/Dev:**
- Spring Boot Maven Plugin — Fat JAR packaging, Lombok exclusion from final artifact
- Spotless Maven Plugin 2.43.0 — Code formatting with Google Java Format 1.19.2 (AOSP style)
- Maven Surefire Plugin — Test execution
- Maven Compiler Plugin — Annotation processor configuration for Lombok
- Spring Boot DevTools — Live reload (`runtime`, `optional`)

## Key Dependencies

**Critical:**
- `org.projectlombok:lombok` — `@Getter`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j` throughout all layers; excluded from production artifact
- `org.postgresql:postgresql` — PostgreSQL JDBC driver (version managed by Spring Boot BOM)
- `org.flywaydb:flyway-database-postgresql` — PostgreSQL-specific Flyway dialect, runs V1–V5 migrations
- `io.micrometer:micrometer-registry-prometheus` — Prometheus metrics scraping at `/actuator/prometheus`
- `spring-boot-starter-json` — Jackson for JSON serialization/deserialization of Kafka events and REST payloads

**Infrastructure:**
- `spring-cloud-starter-circuitbreaker-resilience4j` — wraps `io.github.resilience4j:resilience4j-circuitbreaker`; configured in `config/ResilienceConfig.java`
- `micrometer-registry-prometheus` — runtime dependency, exposes metrics to `infra/prometheus.yml` scrape target

## Configuration

**Environment:**
- All sensitive values in `.env` (gitignored); loaded via `spring.config.import: optional:file:.env[.properties]`
- Template: `.env.example` (committed)
- Placeholders use Spring Boot `${VAR:default}` pattern throughout `application.yaml`
- Key variables: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `KAFKA_BOOTSTRAP_SERVERS`, `MAIL_HOST`, `MAIL_PORT`, `SERVER_PORT`
- Docker-specific overrides in `src/main/resources/application-docker.yml` (profile `docker`)
- Test profile: `src/test/resources/` expected for `application-test.yml`; `@DynamicPropertySource` overrides infra URLs in `BaseIntegrationTest`

**ConfigurationProperties classes (all in `config/` package):**
- `RateLimitProperties` — prefix `notification.rate-limit`, per-channel `maxRequests` + `window: Duration`
- `ResilienceProperties` — prefix `pheme.resilience.email`, circuit breaker parameters
- `RetrySchedulerProperties` — prefix `notification.retry-scheduler`
- All use constructor binding, `@Validated`, no `@Component`; scanned by `@ConfigurationPropertiesScan` on `PhemeNotifyApplication`

**Build:**
- `pom.xml` — single Maven module, no multi-module layout
- Spotless enforces Google Java Format AOSP style; run `./mvnw spotless:apply` to format
- `java.version=21`, `spring-cloud.version=2025.1.1`, `testcontainers.version=1.21.4` in `<properties>`

## Platform Requirements

**Development:**
- Java 21 JDK
- Docker + Docker Compose (for infrastructure: Kafka, PostgreSQL, Redis, Mailpit, Prometheus, Grafana)
- Application runs from IDE on port `8080` (default) or `8081` (see CLAUDE.md §11); infra from `docker-compose.yml`

**Production:**
- Containerized deployment via Docker (Dockerfile not yet present — app service commented out in `docker-compose.yml`)
- Spring profile `docker` activated via `SPRING_PROFILES_ACTIVE=docker`
- PostgreSQL 17, Redis 7 (Alpine), Kafka KRaft mode (`confluentinc/cp-kafka:7.9.0`)

---

*Stack analysis: 2026-05-24*
