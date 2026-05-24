# Codebase Structure

**Analysis Date:** 2026-05-24

## Directory Layout

```
pheme-notify/
├── src/
│   ├── main/
│   │   ├── java/com/pheme/phemenotify/
│   │   │   ├── PhemeNotifyApplication.java   # Bootstrap: @SpringBootApplication, @ConfigurationPropertiesScan, @EnableScheduling
│   │   │   ├── api/
│   │   │   │   ├── ApiPaths.java             # Constant: V1 = "/api/v1"
│   │   │   │   ├── controller/               # REST controllers
│   │   │   │   ├── dto/
│   │   │   │   │   ├── request/              # Inbound DTOs (@Valid, records)
│   │   │   │   │   └── response/             # Outbound DTOs (records)
│   │   │   │   └── exception/                # GlobalExceptionHandler + domain exceptions
│   │   │   ├── config/
│   │   │   │   ├── KafkaConfig.java          # Topic beans, DefaultErrorHandler
│   │   │   │   ├── RedisConfig.java          # RedisTemplate, StringRedisTemplate, Lua script bean
│   │   │   │   ├── ResilienceConfig.java     # CircuitBreaker customizer for "email"
│   │   │   │   ├── JpaConfig.java            # @EnableJpaAuditing
│   │   │   │   ├── JacksonConfig.java        # Registers EventTypeDeserializer
│   │   │   │   ├── ThymeleafConfig.java      # Template resolver configuration
│   │   │   │   ├── RateLimitProperties.java  # @ConfigurationProperties(prefix="notification.rate-limit")
│   │   │   │   ├── ResilienceProperties.java # @ConfigurationProperties(prefix="pheme.resilience.email")
│   │   │   │   ├── RetrySchedulerProperties.java # @ConfigurationProperties(prefix="notification.retry-scheduler")
│   │   │   │   └── validation/
│   │   │   │       ├── PositiveDuration.java         # Custom constraint annotation
│   │   │   │       └── PositiveDurationValidator.java # Constraint implementation
│   │   │   ├── messaging/
│   │   │   │   ├── consumer/
│   │   │   │   │   └── NotificationEventConsumer.java # @KafkaListener + @RetryableTopic + @DltHandler
│   │   │   │   ├── event/
│   │   │   │   │   ├── NotificationEvent.java        # Kafka message record
│   │   │   │   │   └── EventTypeDeserializer.java    # Jackson deserializer using EventTypeRegistry
│   │   │   │   └── retry/
│   │   │   │       └── FailedNotificationRetryScheduler.java # @Scheduled exponential backoff
│   │   │   ├── service/
│   │   │   │   ├── NotificationOrchestrator.java  # Core pipeline: dedup → prefs → rate limit → template → send
│   │   │   │   ├── NotificationService.java       # Query-side: getById for REST
│   │   │   │   ├── PreferenceService.java         # CRUD for UserPreferences
│   │   │   │   ├── RateLimitService.java          # Per-channel limit check via Redis Lua
│   │   │   │   └── TemplateService.java           # Thymeleaf template resolution + rendering
│   │   │   ├── provider/
│   │   │   │   ├── NotificationProvider.java      # Interface: send(event, renderedTemplate)
│   │   │   │   ├── ProviderRegistry.java          # Map<Channel, NotificationProvider>
│   │   │   │   ├── EmailProvider.java             # JavaMailSender + CircuitBreaker
│   │   │   │   ├── SmsProvider.java               # Mock: log + sleep
│   │   │   │   └── PushProvider.java              # Mock
│   │   │   ├── infrastructure/
│   │   │   │   ├── redis/
│   │   │   │   │   ├── RedisDeduplicationAdapter.java  # SETNX dedup:{id} TTL=24h
│   │   │   │   │   └── RedisRateLimitAdapter.java      # Executes rate_limit.lua
│   │   │   │   ├── resilience/                    # Currently empty (planned CircuitBreakerDecorator)
│   │   │   │   └── metrics/                       # Currently empty (planned NotificationMetrics)
│   │   │   ├── persistence/
│   │   │   │   ├── entity/
│   │   │   │   │   ├── Notification.java          # JPA entity, table: notifications
│   │   │   │   │   ├── UserPreferences.java       # JPA entity, table: user_preferences
│   │   │   │   │   ├── FailedNotification.java    # JPA entity, table: failed_notifications, JSONB payload
│   │   │   │   │   ├── Channel.java               # Enum: EMAIL, SMS, PUSH + @JsonCreator
│   │   │   │   │   ├── NotificationStatus.java    # Enum: PENDING, DELIVERED, FAILED, CANCELLED
│   │   │   │   │   ├── EventType.java             # Interface: getCode()
│   │   │   │   │   ├── EventTypeRegistry.java     # @Component, Map<String, EventType>
│   │   │   │   │   └── eventtype/
│   │   │   │   │       ├── OrderCompletedEventType.java
│   │   │   │   │       ├── UserRegisteredEventType.java
│   │   │   │   │       └── PaymentFailedEventType.java
│   │   │   │   ├── converter/
│   │   │   │   │   ├── EventTypeAttributeConverter.java   # @Converter(autoApply=true), uses EventTypeRegistry
│   │   │   │   │   ├── ChannelAttributeConverter.java     # @Converter(autoApply=true), uses EnumUtils
│   │   │   │   │   └── NotificationStatusConverter.java   # @Converter(autoApply=true), uses EnumUtils
│   │   │   │   ├── repository/
│   │   │   │   │   ├── NotificationRepository.java        # JpaRepository + native analytics query
│   │   │   │   │   ├── UserPreferenceRepository.java      # JpaRepository + findByUserId
│   │   │   │   │   └── FailedNotificationRepository.java  # JpaRepository + findByStatusAndNextRetryAtBefore
│   │   │   │   ├── projection/
│   │   │   │   │   └── DeliveryStatsProjection.java       # Interface projection for analytics native query
│   │   │   │   └── mapper/                        # Currently empty (planned NotificationMapper)
│   │   │   └── utils/
│   │   │       └── EnumUtils.java                 # Generic fromValue(Class<T>, String) for enums
│   │   └── resources/
│   │       ├── application.yaml                   # Main config, ${VAR:default} style
│   │       ├── application-docker.yml             # Overrides hosts for Docker network
│   │       ├── db/migration/
│   │       │   ├── V1__create_enums.sql           # PostgreSQL ENUM types
│   │       │   ├── V2__create_user_preferences.sql
│   │       │   ├── V3__create_notifications.sql   # Central analytics table
│   │       │   ├── V4__create_failed_notifications.sql
│   │       │   └── V5__create_user_preference_channels.sql
│   │       ├── redis/
│   │       │   └── rate_limit.lua                 # Sliding-window Lua script (atomic)
│   │       └── templates/
│   │           ├── email/
│   │           │   └── order-completed.html       # Thymeleaf HTML template
│   │           └── sms/
│   │               └── user-registered.txt        # Thymeleaf text template
│   └── test/
│       ├── java/com/pheme/phemenotify/
│       │   ├── api/controller/                    # Controller tests
│       │   ├── api/exception/                     # Exception handler tests
│       │   ├── config/                            # Config/properties tests
│       │   ├── infrastructure/redis/              # Redis adapter tests
│       │   ├── messaging/consumer/                # Kafka consumer tests
│       │   ├── messaging/event/                   # Event deserialization tests
│       │   ├── messaging/retry/                   # Retry scheduler tests
│       │   ├── persistence/converter/             # Converter tests
│       │   ├── persistence/entity/                # Entity tests
│       │   ├── persistence/repository/            # Repository / integration tests
│       │   ├── provider/                          # Provider tests (incl. CB integration)
│       │   ├── service/                           # Service unit tests
│       │   └── util/                             # Object Mother test data classes
│       └── resources/
│           └── application-test.yml              # Testcontainers profile overrides
├── .planning/codebase/                           # GSD codebase map documents
├── infra/
│   └── prometheus.yml                            # Prometheus scrape config
├── docker-compose.yml                            # All dev services
├── .env                                          # Secrets (gitignored)
├── .env.example                                  # Template for team (committed)
├── pom.xml                                       # Maven build
└── CLAUDE.md                                     # AI Tech Lead context document
```

## Directory Purposes

**`api/`:**
- Purpose: HTTP interface — controllers, request/response DTOs, exception handling
- Contains: `@RestController` classes, Java records for DTOs, `GlobalExceptionHandler`, domain exception classes
- Key files: `ApiPaths.java` (version constant), `GlobalExceptionHandler.java` (Problem Detail RFC 9457)

**`config/`:**
- Purpose: All Spring `@Configuration` and `@ConfigurationProperties` classes
- Contains: Infrastructure configs (Kafka, Redis, JPA), properties beans (RateLimit, Resilience, RetryScheduler), custom `@PositiveDuration` validation constraint
- Rule: Never use `@Component` on properties classes; rely on `@ConfigurationPropertiesScan`

**`messaging/`:**
- Purpose: Kafka boundary — consume events, serialize/deserialize, scheduled retry
- Contains: `NotificationEventConsumer` (Kafka entry point), `NotificationEvent` record, `EventTypeDeserializer`, `FailedNotificationRetryScheduler`

**`service/`:**
- Purpose: Business logic and orchestration
- Contains: All `@Service` classes; the pipeline lives in `NotificationOrchestrator`

**`provider/`:**
- Purpose: Channel delivery implementations behind the `NotificationProvider` strategy interface
- Contains: `NotificationProvider` interface, concrete providers, `ProviderRegistry`

**`infrastructure/redis/`:**
- Purpose: Low-level Redis adapters isolating Spring Data Redis calls from service logic
- Contains: `RedisDeduplicationAdapter`, `RedisRateLimitAdapter`

**`persistence/entity/`:**
- Purpose: JPA entities and domain value types
- Contains: Three JPA entities, two enums (`Channel`, `NotificationStatus`), `EventType` interface, `EventTypeRegistry`, concrete event type implementations

**`persistence/entity/eventtype/`:**
- Purpose: Concrete `EventType` implementations — each is a Spring `@Component` auto-registered in `EventTypeRegistry`
- To add a new event type: create a class here implementing `EventType`, annotate `@Component`, define `static final String CODE`

**`persistence/converter/`:**
- Purpose: JPA `AttributeConverter` implementations mapping Java types to PostgreSQL types
- Contains: converters for `EventType` ↔ VARCHAR, `Channel` ↔ `notification_channel` ENUM, `NotificationStatus` ↔ `notification_status` ENUM

**`persistence/repository/`:**
- Purpose: Spring Data JPA repositories
- Contains: `NotificationRepository` (includes native analytics query), `UserPreferenceRepository`, `FailedNotificationRepository`

**`persistence/projection/`:**
- Purpose: JPA interface-based projections for native query results
- Contains: `DeliveryStatsProjection` (analytics query result type)

**`utils/`:**
- Purpose: Generic utility classes with no business logic
- Contains: `EnumUtils.fromValue(Class<T>, String)` used by enum attribute converters

**`resources/db/migration/`:**
- Purpose: Flyway migrations applied in order V1→V5
- Rule: V1 must remain first — it creates PostgreSQL ENUM types that V3/V4 depend on

**`resources/redis/`:**
- Purpose: Lua scripts executed atomically via `RedisTemplate.execute()`
- Contains: `rate_limit.lua` (sliding-window counter, loaded as `DefaultRedisScript<Long>` bean in `RedisConfig`)

**`resources/templates/`:**
- Purpose: Thymeleaf templates organized by channel subdirectory
- Naming convention: `{channel}/{event-type-code-kebab-case}.{html|txt}`
- Example: `email/order-completed.html`, `sms/user-registered.txt`

## Key File Locations

**Entry Points:**
- `src/main/java/com/pheme/phemenotify/PhemeNotifyApplication.java`: application bootstrap
- `src/main/java/com/pheme/phemenotify/messaging/consumer/NotificationEventConsumer.java`: Kafka entry point
- `src/main/java/com/pheme/phemenotify/api/controller/NotificationController.java`: REST status endpoint
- `src/main/java/com/pheme/phemenotify/api/controller/PreferenceController.java`: REST preferences endpoint

**Configuration:**
- `src/main/resources/application.yaml`: primary configuration
- `src/main/resources/application-docker.yml`: Docker network overrides
- `src/test/resources/application-test.yml`: Testcontainers overrides

**Core Business Logic:**
- `src/main/java/com/pheme/phemenotify/service/NotificationOrchestrator.java`: main pipeline
- `src/main/java/com/pheme/phemenotify/provider/ProviderRegistry.java`: channel dispatch
- `src/main/resources/redis/rate_limit.lua`: atomic rate-limit script

**Analytics:**
- `src/main/java/com/pheme/phemenotify/persistence/repository/NotificationRepository.java`: native SQL analytics query (lines 17–37)
- `src/main/java/com/pheme/phemenotify/persistence/projection/DeliveryStatsProjection.java`: projection interface

**Testing Utilities:**
- `src/test/java/com/pheme/phemenotify/util/`: Object Mother classes (suffix `TestData.java`)

## Naming Conventions

**Files:**
- Java classes: PascalCase (`NotificationOrchestrator.java`, `RedisRateLimitAdapter.java`)
- Configuration properties classes: suffix `Properties` (`RateLimitProperties.java`, `ResilienceProperties.java`)
- Converters: suffix `Converter` or `AttributeConverter` (`ChannelAttributeConverter.java`)
- Test data helpers: suffix `TestData.java` (`UserPreferencesTestData.java`)
- Flyway migrations: `V{N}__{description_snake_case}.sql`
- Thymeleaf templates: kebab-case matching event type code (`order-completed.html`, `user-registered.txt`)

**Directories:**
- lowercase, singular or plural as appropriate to content (`entity/`, `converter/`, `repository/`, `projection/`)
- Infrastructure sub-grouping by technology (`infrastructure/redis/`, `infrastructure/metrics/`, `infrastructure/resilience/`)

**Methods:**
- Test methods: `should{Behavior}_when{Condition}` (e.g., `shouldSkipDuplicate_whenEventAlreadySeen`)

## Where to Add New Code

**New notification channel (e.g., PUSH real implementation):**
- Add enum value to `Channel` if not present: `persistence/entity/Channel.java`
- Create provider: `provider/{Channel}Provider.java` implementing `NotificationProvider`
- Register in: `provider/ProviderRegistry.java` constructor
- Add rate limit config: `config/RateLimitProperties.java` + `application.yaml`
- Add templates: `resources/templates/{channel}/`

**New event type:**
- Create `@Component` class in `persistence/entity/eventtype/{EventName}EventType.java` implementing `EventType`
- Define `static final String CODE = "EVENT_NAME"` and return it from `getCode()`
- Add Thymeleaf templates as needed in `resources/templates/`
- No other changes needed — `EventTypeRegistry` auto-discovers all `EventType` beans

**New REST endpoint:**
- Controller: `api/controller/{Feature}Controller.java`
- Request DTO (if needed): `api/dto/request/{Feature}Request.java`
- Response DTO (if needed): `api/dto/response/{Feature}Response.java`
- Service: `service/{Feature}Service.java`

**New Flyway migration:**
- File: `src/main/resources/db/migration/V{N+1}__{description}.sql`
- Increment N from current highest (V5 as of this analysis)

**New `@ConfigurationProperties`:**
- Class: `config/{Feature}Properties.java` with `@ConfigurationProperties(prefix="...")`, `@Validated`, `@Getter`, constructor binding
- No `@Component` — `@ConfigurationPropertiesScan` on `PhemeNotifyApplication` handles registration
- Add `@Validated` for fail-fast startup validation

**Infrastructure adapters:**
- Redis: `infrastructure/redis/{Name}Adapter.java`
- Metrics: `infrastructure/metrics/NotificationMetrics.java` (planned, not yet created)

## Special Directories

**`.planning/codebase/`:**
- Purpose: GSD codebase map documents (STACK.md, INTEGRATIONS.md, ARCHITECTURE.md, STRUCTURE.md, CONVENTIONS.md, CONCERNS.md)
- Generated: Yes (by `/gsd-map-codebase` command)
- Committed: Yes

**`infra/`:**
- Purpose: External infrastructure configuration files not embedded in the app
- Contains: `prometheus.yml` (Prometheus scrape config targeting `host.docker.internal:8081/actuator/prometheus`)
- Committed: Yes

**`src/main/resources/db/migration/`:**
- Purpose: Flyway versioned SQL migrations
- Generated: No (hand-written)
- Committed: Yes
- Rule: Never reorder or edit existing migration files after they have been applied to any environment

**`src/main/resources/static/`:**
- Purpose: Static web assets (currently empty)
- Generated: No
- Committed: Yes (as empty placeholder)

---

*Structure analysis: 2026-05-24*
