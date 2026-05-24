# Coding Conventions

**Analysis Date:** 2026-05-24

## Naming Patterns

**Files:**
- Classes: PascalCase matching class name (`NotificationOrchestrator.java`, `RedisRateLimitAdapter.java`)
- Interfaces: PascalCase, named by capability (`NotificationProvider.java`, `EventType.java`)
- Enums: PascalCase (`Channel.java`, `NotificationStatus.java`)
- Config properties classes: suffix `Properties` (`RateLimitProperties.java`, `ResilienceProperties.java`)
- Converter classes: suffix `AttributeConverter` (`ChannelAttributeConverter.java`)
- Adapter classes: suffix `Adapter` (`RedisDeduplicationAdapter.java`, `RedisRateLimitAdapter.java`)
- Projection interfaces: suffix `Projection` (`DeliveryStatsProjection.java`)
- Test data utilities: suffix `TestData` (`NotificationTestData.java`, `PreferenceTestData.java`)

**Methods:**
- camelCase throughout
- Boolean queries: `isNew()`, `isAllowed()`, `isEnabled()`
- Factory methods on entities: `Notification.pending(...)` — static named constructors
- Service CRUD: `getByUserId()`, `upsert()` (not `findBy`, not `create/update` split)
- Consumer entry points: `handleEvent()`, `handleDlt()`

**Variables and Fields:**
- camelCase — `idempotencyKey`, `enabledChannels`, `rateLimitAdapter`
- Constants: `UPPER_SNAKE_CASE` with `private static final` (`IDEMPOTENCY_KEY_FORMAT`, `KEY_PREFIX`, `TTL`)
- Magic numbers always extracted to named constants — never inline in `@Bean` or method body
  - Example in `src/main/java/com/pheme/phemenotify/config/KafkaConfig.java`:
    ```java
    private static final int MAIN_TOPIC_PARTITIONS = 3;
    private static final long ERROR_HANDLER_BACKOFF_MS = 1000L;
    ```

**Types:**
- Records for DTOs and events: `NotificationEvent`, `NotificationResponse`, `CreatePreferenceRequest`
- `@Builder` + `@Getter` + `@Setter` + `@NoArgsConstructor` + `@AllArgsConstructor` on JPA entities (not records)
- Configuration properties: `@Getter` only + constructor binding (no setters, except when Spring Boot requires setter-based binding for top-level fields with nested records)

## Code Style

**Formatting:**
- No automated formatter config detected (no `.editorconfig`, `google-java-format`, or Checkstyle)
- IntelliJ default formatting implied
- 4-space indentation throughout
- Opening braces on same line

**Annotations order on `@Service`/`@Component` classes:**
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class Foo { ... }
```

**Annotations order on JPA entities:**
```java
@Entity
@Table(name = "table_name")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class FooEntity { ... }
```

## Import Organization

**Order (IntelliJ default observed):**
1. `java.*` and `jakarta.*`
2. `org.*` (Spring, Thymeleaf, Resilience4j, etc.)
3. `com.pheme.*` (project-internal)
4. `lombok.*`

**Wildcard imports:** Used selectively for Mockito static imports (`import static org.mockito.Mockito.*`) and MockMvc request/result builders.

**Path Aliases:** None — full package names used throughout.

## Dependency Injection

- Always constructor injection via `@RequiredArgsConstructor` (Lombok)
- `@Autowired` on fields is not used anywhere in the codebase
- Exception: `EmailProvider` (`src/main/java/com/pheme/phemenotify/provider/EmailProvider.java`) uses an explicit constructor because the `CircuitBreaker` instance must be created and cached from the factory during construction
- `@MockitoBean` (Spring Boot 3.4+) used in integration tests — never the deprecated `@MockBean`

## Error Handling

**Domain exceptions:**
- All extend `RuntimeException` (unchecked): `ResourceNotFoundException`, `RateLimitExceededException`, `TemplateNotFoundException`
- Located in `src/main/java/com/pheme/phemenotify/api/exception/`
- Cause is always preserved in constructors: `throw new TemplateNotFoundException(templateName, e)`

**Error messages stored in DB:**
- Normalized error codes only: `"RATE_LIMIT_EXCEEDED"`, `"SEND_FAILED:RuntimeException"`
- Never store `e.getMessage()` in DB — detailed messages go to logs only
- Pattern: `"SEND_FAILED:" + e.getClass().getSimpleName()`

**Controller error handling:**
- `GlobalExceptionHandler extends ResponseEntityExceptionHandler` in `src/main/java/com/pheme/phemenotify/api/exception/GlobalExceptionHandler.java`
- Returns `ProblemDetail` (RFC 9457) with `type` URI, `title`, `status`, `detail`
- Type URIs follow pattern: `https://pheme.com/errors/{kebab-case-name}`
- Never returns stack traces to clients
- Catch-all `@ExceptionHandler(Exception.class)` generates a random `errorId` UUID for log correlation

**Partial failure pattern in `NotificationOrchestrator`:**
- Each channel attempt is wrapped in its own `try/catch` in `processChannel()`
- Exceptions never propagate out of the channel loop
- Flow: save `PENDING` → process → update to `DELIVERED` or `FAILED`
- File: `src/main/java/com/pheme/phemenotify/service/NotificationOrchestrator.java`

## Logging

**Framework:** SLF4J via Lombok `@Slf4j` annotation on the class

**Level guidelines:**

| Level | When |
|-------|------|
| `log.info` | Successful delivery: `"Notification delivered to user {} via {}"` |
| `log.warn` | Expected skips: `"Duplicate event {}, skipping"`, rate limit exceeded |
| `log.error` | Unexpected failures: `"Failed to send via {}: {}"`, circuit breaker open |

**Message format:** `{past-tense verb} {subject} {context}` using named SLF4J `{}` placeholders.

**Rules:**
- Always include `userId` or `eventId` in warn/error messages for incident traceability
- `log.error(msg, ..., e)` — exception always passed as last argument for automatic stacktrace output
- Never `"Error: " + e.getMessage()` — string concatenation without stacktrace is forbidden
- `log.debug` is for development-only details, not committed to service code

## Comments

**Javadoc on `@ConfigurationProperties` classes:**
- Class-level Javadoc describes what it configures and the yaml prefix
- Field-level Javadoc states the yaml key and field semantics
- See `src/main/java/com/pheme/phemenotify/config/ResilienceProperties.java` as canonical example

**Inline comments:** Used only for non-obvious decisions (e.g., `// make config?`, `// Ignored - we're testing circuit breaker behavior`)

**No comments in service/controller logic** unless explaining a non-obvious algorithm

## Function Design

**Size:** Methods are short and single-purpose. Private helpers are extracted proactively:
- `resolvePreferences()` in `NotificationOrchestrator`
- `resolveTemplateName()` in `TemplateService`
- `buildEventPayload()` in `NotificationEventConsumer`
- `toResponse()` in `PreferenceService`

**Parameters:**
- Prefer strongly-typed parameters over raw maps
- `Map<String, Object>` accepted only where library contract requires it (Thymeleaf context variables)

**Return values:**
- `boolean` for guard/eligibility checks: `isNew()`, `isAllowed()`, `processRetry()`
- `Optional<T>` from all repository methods — never `null`
- `void` for fire-and-forget operations: `send()`, `process()`
- Records for all DTO responses and requests

## ConfigurationProperties Pattern

All `@ConfigurationProperties` classes follow these rules (enforced in CLAUDE.md §14):

- **No `@Component`** — registered via `@ConfigurationPropertiesScan` on `PhemeNotifyApplication`
- **Constructor binding:** `final` fields + single public constructor
- **`@Validated`** on the class for fail-fast startup validation
- **`@Min(1)`**, **`@Positive`**, **`@NotNull`** on fields as appropriate
- **`Duration` type** for all time-based fields — never `long`/`int` with units in the name
- **`@PositiveDuration`** custom constraint from `src/main/java/com/pheme/phemenotify/config/validation/` for strictly positive durations

Canonical example: `src/main/java/com/pheme/phemenotify/config/ResilienceProperties.java`

## JPA Entity Conventions

- `@Builder.Default` required for any field with an initializer inside a `@Builder` class
- PostgreSQL native ENUMs are mapped via `AttributeConverter<T, PGobject>` with `@Converter(autoApply=true)` — **never** `@Enumerated(EnumType.STRING)`
- `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` used with `@Convert(disableConversion=true)` on native enum columns
- JPA auditing via `@CreatedDate` / `@LastModifiedDate` + `@EntityListeners(AuditingEntityListener.class)`, enabled by `JpaConfig` (`@EnableJpaAuditing`)
- Static factory methods for common creation patterns: `Notification.pending(userId, eventType, channel, key)`

## Enum Conventions

- Each enum stores a `value` field and overrides `toString()` with `@JsonValue`
- `@JsonCreator` static factory method delegates to `EnumUtils.fromValue()` for case-insensitive deserialization
- `EnumUtils` (`src/main/java/com/pheme/phemenotify/utils/EnumUtils.java`) is a `final` utility class with a private constructor throwing `UnsupportedOperationException`

## Module Design

**Exports:**
- All Spring-managed beans are `public`
- Internal helpers (private methods, private constants) are kept non-public

**Barrel Files:** Not used — Java packages serve as module boundaries

---

*Convention analysis: 2026-05-24*
