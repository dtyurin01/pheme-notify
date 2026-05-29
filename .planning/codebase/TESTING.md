# Testing Patterns

**Analysis Date:** 2026-05-24

## Test Framework

**Runner:**
- JUnit 5 (JUnit Jupiter) — via `spring-boot-starter-test` (Spring Boot 4.0.5)
- Config: no separate `junit-platform.properties`; Maven Surefire plugin handles discovery

**Assertion Library:**
- AssertJ — `org.assertj.core.api.Assertions.*`
- Spring MockMvc matchers for HTTP layer tests

**Mocking:**
- Mockito — `@ExtendWith(MockitoExtension.class)` for unit tests
- `@MockitoBean` (Spring Boot 3.4+) for integration tests — replaces the deprecated `@MockBean`

**Run Commands:**
```bash
./mvnw test                  # Run all tests
./mvnw test -Dtest=ClassName # Run single test class
./mvnw verify                # Run all tests including integration
```

## Test File Organization

**Location:**
- Mirror of `src/main/java` package structure under `src/test/java/com/pheme/phemenotify/`
- Integration tests live in the same package as the class under test
- Cross-cutting test utilities in `src/test/java/com/pheme/phemenotify/util/`

**Naming:**
- Unit tests: `{ClassName}Test.java` — e.g., `NotificationOrchestratorTest.java`
- Integration tests with Spring context: `{ClassName}IntegrationTest.java` — e.g., `EmailProviderCircuitBreakerIntegrationTest.java`, `TemplateServiceIntegrationTest.java`
- Repository tests: `{ClassName}Test.java` extending `BaseIntegrationTest`

**Structure:**
```
src/test/java/com/pheme/phemenotify/
├── BaseIntegrationTest.java          — shared Testcontainers base
├── api/
│   ├── controller/
│   │   ├── NotificationControllerTest.java   — @WebMvcTest
│   │   └── PreferenceControllerTest.java     — @WebMvcTest
│   └── exception/
│       └── GlobalExceptionHandlerTest.java   — @WebMvcTest
├── infrastructure/
│   └── redis/
│       ├── RedisDeduplicationAdapterTest.java — BaseIntegrationTest
│       └── RedisRateLimitAdapterTest.java     — BaseIntegrationTest
├── messaging/
│   ├── consumer/
│   │   └── NotificationEventConsumerTest.java — @ExtendWith(MockitoExtension)
│   ├── event/
│   │   └── EventTypeDeserializerTest.java     — @ExtendWith(MockitoExtension)
│   └── retry/
│       └── FailedNotificationRetrySchedulerTest.java — @ExtendWith(MockitoExtension)
├── persistence/
│   ├── converter/
│   │   ├── ChannelAttributeConverterTest.java
│   │   └── NotificationStatusConverterTest.java
│   ├── entity/
│   │   └── EventTypeRegistryTest.java
│   └── repository/
│       ├── NotificationRepositoryTest.java    — BaseIntegrationTest
│       └── UserPreferenceRepositoryTest.java  — BaseIntegrationTest
├── provider/
│   ├── EmailProviderTest.java                 — @ExtendWith(MockitoExtension)
│   └── EmailProviderCircuitBreakerIntegrationTest.java — BaseIntegrationTest
├── service/
│   ├── DeduplicationServiceTest.java          — @ExtendWith(MockitoExtension)
│   ├── NotificationOrchestratorTest.java      — @ExtendWith(MockitoExtension)
│   ├── PreferenceServiceTest.java             — @ExtendWith(MockitoExtension)
│   ├── RateLimitServiceTest.java              — @ExtendWith(MockitoExtension)
│   ├── TemplateServiceTest.java               — @ExtendWith(MockitoExtension)
│   └── TemplateServiceIntegrationTest.java    — @SpringBootTest
└── util/
    ├── FailedNotificationTestData.java
    ├── NoOpCircuitBreakerFactory.java
    ├── NotificationTestData.java
    └── PreferenceTestData.java
```

## Test Structure

**Unit test suite organization:**
```java
@ExtendWith(MockitoExtension.class)
class NotificationOrchestratorTest {

    @Mock
    private UserPreferenceRepository userPreferenceRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @InjectMocks
    private NotificationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        // lenient() used for stubs shared across tests that may not all be called
        lenient().when(notificationRepository.save(any()))
                 .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void shouldSkip_whenEventIsDuplicate() { ... }
}
```

**`@WebMvcTest` controller tests:**
```java
@WebMvcTest(PreferenceController.class)
public class PreferenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PreferenceService preferenceService;

    @Test
    void shouldReturn200WithPreferenceResponse_whenUserExists() throws Exception {
        when(preferenceService.getByUserId("user-1")).thenReturn(PreferenceTestData.defaultResponse());

        mockMvc.perform(get(ApiPaths.V1 + "/users/user-1/preferences"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.userId").value("user-1"));
    }
}
```

**Integration test (extends `BaseIntegrationTest`):**
```java
public class RedisRateLimitAdapterTest extends BaseIntegrationTest {

    @Autowired
    private RedisRateLimitAdapter rateLimitAdapter;

    @AfterEach
    void cleanUp() {
        stringRedisTemplate.getConnectionFactory()
                .getConnection().serverCommands().flushDb();
    }

    @Test
    void shouldBlock_whenLimitReached() { ... }
}
```

**Patterns:**
- `@BeforeEach setUp()` for test fixtures and lenient stub configuration
- `@AfterEach cleanup()` for repository `deleteAll()` and Redis `flushDb()` in integration tests
- `ArgumentCaptor` to assert on objects passed to mocked dependencies
- `lenient().when(...)` for stubs that are not invoked in every test in the class

## BaseIntegrationTest

All integration tests inherit from `src/test/java/com/pheme/phemenotify/BaseIntegrationTest.java`:

```java
@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @MockitoBean
    FailedNotificationRetryScheduler retryScheduler; // prevents scheduler from running during tests

    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17")
                    .withDatabaseName("pheme_test").withUsername("pheme").withPassword("pheme");

    static final RedisContainer redis =
            new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.9.0"));

    static {
        Startables.deepStart(postgres, redis, kafka).join(); // parallel startup
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        // ...
    }
}
```

Key details:
- `Startables.deepStart(...)` starts all three containers in parallel
- `FailedNotificationRetryScheduler` is always mocked to prevent background retries interfering with tests
- `@ActiveProfiles("test")` activates `application-test.yml`

## Mocking

**Framework:** Mockito

**Unit test patterns:**
```java
// Standard mock setup
@Mock private RedisRateLimitAdapter rateLimitAdapter;
@InjectMocks private RateLimitService rateLimitService;

// Stub return value
when(rateLimitAdapter.isAllowed(any(), any(), anyLong(), anyInt())).thenReturn(true);

// Stub void method to throw
doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

// Verify interaction
verify(rateLimitAdapter).isAllowed("user-1", "EMAIL", 3_600_000L, 5);
verify(notificationRepository, never()).save(any());
verifyNoInteractions(notificationRepository);

// Capture argument for assertions
ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
verify(notificationRepository, times(2)).save(captor.capture());
assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo(NotificationStatus.DELIVERED);
```

**Integration test patterns:**
```java
// Spring bean replacement
@MockitoBean
private JavaMailSender mailSender;

// Reset between tests
@BeforeEach
void setUp() {
    circuitBreakerRegistry.find("email").ifPresent(CircuitBreaker::reset);
    clearInvocations(mailSender);
}
```

**What to mock:**
- All external I/O: `JavaMailSender`, `StringRedisTemplate` in unit tests
- Repository layer in service unit tests
- Scheduled jobs (`FailedNotificationRetryScheduler`) in all integration tests to prevent interference

**What NOT to mock:**
- Redis in `RedisRateLimitAdapterTest` and `RedisDeduplicationAdapterTest` — use real Redis via Testcontainers to test Lua script atomicity
- PostgreSQL in repository tests — use real DB via Testcontainers to validate native queries and JPA converters

## Fixtures and Factories

**Object Mother pattern** — used for objects shared across 3+ test classes:

```java
// src/test/java/com/pheme/phemenotify/util/NotificationTestData.java
public class NotificationTestData {
    public static NotificationEvent defaultEvent() {
        return new NotificationEvent("event-1", "user-1",
                new OrderCompletedEventType(), Channel.EMAIL,
                Map.of("email", "user@example.com"), Instant.now());
    }
    public static NotificationEvent eventWithPayload(Map<String, String> payload) { ... }
    public static Notification defaultEntity(UUID id) { ... }
    public static Notification entityWith(UUID id, NotificationStatus status, String errorMessage) { ... }
}

// src/test/java/com/pheme/phemenotify/util/PreferenceTestData.java
public class PreferenceTestData {
    public static UserPreferences defaultEntity() { ... }
    public static UserPreferences entityWith(String userId, Set<Channel> channels, ...) { ... }
    public static UserPreferences entityWithNoChannels() { ... }
    public static CreatePreferenceRequest defaultRequest() { ... }
    public static PreferenceResponse defaultResponse() { ... }
}
```

Available Object Mother classes:
- `src/test/java/com/pheme/phemenotify/util/NotificationTestData.java` — `NotificationEvent`, `Notification`, `NotificationResponse`
- `src/test/java/com/pheme/phemenotify/util/PreferenceTestData.java` — `UserPreferences`, `CreatePreferenceRequest`, `PreferenceResponse`
- `src/test/java/com/pheme/phemenotify/util/FailedNotificationTestData.java` — `FailedNotification` with configurable `retryCount`

**Rule:** Use a private method inside the test class until the object is needed in 3+ test classes, then promote to an Object Mother in `util/`.

**Test utilities (non-data):**
- `src/test/java/com/pheme/phemenotify/util/NoOpCircuitBreakerFactory.java` — a `CircuitBreakerFactory` that transparently delegates to the supplier (no circuit breaking), used to isolate provider tests from CB logic

## Coverage

**Requirements:** >80% service layer coverage (per CLAUDE.md §8)

**Enforcement:** No automated coverage gate in CI detected; coverage is a manual review concern.

**View Coverage:**
```bash
./mvnw test jacoco:report       # if JaCoCo plugin is added
# or use IntelliJ built-in "Run with Coverage"
```

## Test Types

**Unit Tests (`@ExtendWith(MockitoExtension.class)`):**
- Scope: single class, all dependencies mocked
- No Spring context loaded — fast execution
- Used for: all `service/`, `provider/`, `messaging/consumer/`, `messaging/retry/`, `persistence/converter/` classes
- Example: `src/test/java/com/pheme/phemenotify/service/NotificationOrchestratorTest.java`

**Web Layer Tests (`@WebMvcTest`):**
- Scope: one controller + `GlobalExceptionHandler`, service mocked via `@MockitoBean`
- Loads only web layer (no JPA, no Redis)
- Tests HTTP status codes, JSON response shape, and Problem Detail format
- Example: `src/test/java/com/pheme/phemenotify/api/controller/PreferenceControllerTest.java`

**Integration Tests (extends `BaseIntegrationTest`):**
- Scope: full Spring context + real Testcontainers (PostgreSQL 17, Redis 7, Kafka 7.9.0)
- Used for: Redis adapters, JPA repositories, circuit breaker behavior, Thymeleaf rendering
- Examples:
  - `src/test/java/com/pheme/phemenotify/infrastructure/redis/RedisRateLimitAdapterTest.java` — Lua script atomicity
  - `src/test/java/com/pheme/phemenotify/provider/EmailProviderCircuitBreakerIntegrationTest.java` — CB state transitions
  - `src/test/java/com/pheme/phemenotify/persistence/repository/NotificationRepositoryTest.java` — native query + JPA converters

**Lightweight Integration Tests (`@SpringBootTest` without `BaseIntegrationTest`):**
- `src/test/java/com/pheme/phemenotify/service/TemplateServiceIntegrationTest.java` — loads full context to test real Thymeleaf rendering, but does not use Testcontainers infrastructure

## Common Patterns

**Test method naming:** `should{Behavior}_when{Condition}`
```
shouldSkip_whenEventIsDuplicate
shouldMarkAsFailed_whenProviderThrows
shouldBlock_whenLimitReached
shouldReturn404_whenUserNotFound
```

**Async/Time Testing:**
```java
// For sliding window expiry — Thread.sleep used with short windows
long shortWindow = 200L;
Thread.sleep(shortWindow * 3); // wait for window to expire
boolean allowed = rateLimitAdapter.isAllowed("user-1", "EMAIL", shortWindow, MAX);
assertThat(allowed).isTrue();
```

**Exception Testing:**
```java
// assertThatThrownBy for expected exceptions
assertThatThrownBy(() -> rateLimitService.checkLimit("user-1", Channel.EMAIL))
    .isInstanceOf(RateLimitExceededException.class)
    .hasMessageContaining("user-1");

// assertThatCode for "no exception" assertions
assertThatCode(() -> rateLimitService.checkLimit("user-1", Channel.EMAIL))
    .doesNotThrowAnyException();

// hasCause for wrapped exceptions
assertThatThrownBy(() -> templateService.render(...))
    .isInstanceOf(TemplateNotFoundException.class)
    .hasCause(originalException);
```

**State verification with ArgumentCaptor:**
```java
ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
verify(notificationRepository, times(2)).save(captor.capture());
// Check first save was PENDING, second was DELIVERED
assertThat(captor.getAllValues().get(0).getStatus()).isEqualTo(NotificationStatus.PENDING);
assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo(NotificationStatus.DELIVERED);
```

**`@DisplayName`:** Used occasionally in repository tests for human-readable descriptions. Not required but acceptable.

---

*Testing analysis: 2026-05-24*
