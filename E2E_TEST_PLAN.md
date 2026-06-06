# E2E Test Plan — Pheme Notify

> Все E2E тесты наследуют `BaseIntegrationTest` (Testcontainers: Postgres 17, Redis 7, Kafka 7.9.0).  
> Kafka-тесты используют `KafkaTemplate` для отправки + `Awaitility` (timeout 10s) для ожидания.  
> `FailedNotificationRetryScheduler` замокан в `BaseIntegrationTest` — управляется вручную в тестах группы RS.

---

## Тест-классы

| Класс | Группа сценариев |
|-------|-----------------|
| `NotificationPipelineE2ETest` | Happy Path, Dedup, Preferences, Partial Failure, Template, Email payload |
| `RateLimitE2ETest` | Rate Limiting |
| `KafkaDltE2ETest` | Kafka Retry + DLT |
| `RetrySchedulerE2ETest` | Retry Scheduler |
| `AnalyticsE2ETest` | Analytics SQL + Redis Cache |
| `ApiEdgeCaseTest` | REST API edge cases |

---

## NotificationPipelineE2ETest

### H1 — Happy path: single channel EMAIL

```
given:  UserPreferences(userId="user-1", enabledChannels=[EMAIL])
        NotificationEvent(id="evt-h1", userId="user-1",
          eventType=ORDER_COMPLETED, payload={email: "test@test.com", orderId: "42", amount: "100"})
when:   KafkaTemplate отправляет в "notification.events"
then:   Awaitility → notifications WHERE idempotency_key="evt-h1:EMAIL"
        notification.status = DELIVERED
        notification.sent_at IS NOT NULL
        Redis key "dedup:evt-h1" существует (TTL ~24h)
```

**Метод:** `shouldDeliverEmailNotification_whenValidEventReceived`

---

### H2 — Happy path: multi-channel EMAIL + SMS

```
given:  UserPreferences(userId="user-2", enabledChannels=[EMAIL, SMS])
        NotificationEvent(id="evt-h2", payload={email: "test@test.com"})
when:   отправить событие
then:   notifications WHERE user_id="user-2" → 2 записи
        EMAIL.status = DELIVERED
        SMS.status   = DELIVERED
```

**Метод:** `shouldDeliverToAllEnabledChannels_whenUserHasMultipleChannels`

---

### D1 — Дедупликация: тот же event.id дважды

```
given:  UserPreferences(userId="user-3", enabledChannels=[EMAIL])
        2 NotificationEvent с одинаковым id="evt-d1"
when:   оба события отправлены в Kafka с паузой 100ms
then:   notifications WHERE user_id="user-3" → ровно 1 запись (status=DELIVERED)
        второе событие пропущено (dedup Redis SETNX вернул false)
```

**Метод:** `shouldSkipDuplicateEvent_whenSameEventIdReceived`

---

### D2 — Дедупликация на уровне DB (idempotency_key)

```
given:  В таблице notifications уже есть запись с idempotency_key="evt-d2:EMAIL"
        UserPreferences(userId="user-d2", enabledChannels=[EMAIL])
        Новый NotificationEvent(id="evt-d2") (dedup в Redis НЕТ — другой Redis state)
when:   событие обрабатывается
then:   DataIntegrityViolationException поглощается
        Новая запись не создаётся
        Логируется warn "Duplicate notification for key evt-d2:EMAIL"
```

**Метод:** `shouldSkipChannel_whenIdempotencyKeyAlreadyExistsInDb`

---

### P1 — Нет preferences в БД

```
given:  В user_preferences нет записи для userId="user-no-prefs"
        NotificationEvent(id="evt-p1", userId="user-no-prefs")
when:   событие обрабатывается
then:   notifications таблица не содержит записей для user-no-prefs
        Логируется warn "No user preferences found for user user-no-prefs"
```

> ⚠️ **Баг (P1-BUG):** dedup происходит ДО проверки preferences в `process()`.
> Redis ключ `dedup:evt-p1` БУДЕТ создан, хотя уведомление не отправлено.
> Если пользователь создаст preferences позже и отправитель пошлёт то же событие повторно —
> оно будет проигнорировано как дубликат. Решение: либо перенести dedup после проверки preferences,
> либо не помечать как dedup если preferences не найдены.

**Метод:** `shouldSkipEvent_whenUserHasNoPreferences`

---

### P2 — Пустой список enabledChannels

```
given:  UserPreferences(userId="user-empty", enabledChannels=[])
        NotificationEvent(userId="user-empty")
when:   событие обрабатывается
then:   notifications таблица пуста для user-empty
        Логируется warn "User user-empty has no enabled channels, skipping"
```

**Метод:** `shouldSkipEvent_whenUserHasNoEnabledChannels`

---

### P3 — Orchestrator итерирует enabledChannels, игнорируя event.channel

```
given:  UserPreferences(userId="user-sms-only", enabledChannels=[SMS])
        NotificationEvent(channel=EMAIL) — event.channel игнорируется orchestrator'ом
when:   событие обрабатывается
then:   notifications → 1 запись: channel=SMS, status=DELIVERED
        EMAIL-запись отсутствует
```

> ℹ️ Это осознанное архитектурное решение: orchestrator доставляет по ВСЕМ каналам пользователя,
> не по каналу события. Ретрай (`processRetry`) — наоборот, только по event.channel.

**Метод:** `shouldSendOnlyToEnabledChannels_ignoringEventChannel`

---

### PF1 — Partial failure: EMAIL delivered, SMS failed

```
given:  UserPreferences(userId="user-pf1", enabledChannels=[EMAIL, SMS])
        SmsProvider замокан → бросает RuntimeException
        NotificationEvent(payload={email: "test@test.com"})
when:   событие обрабатывается
then:   notifications WHERE channel=EMAIL → status=DELIVERED
        notifications WHERE channel=SMS  → status=FAILED, error_message="SEND_FAILED:RuntimeException"
        Kafka consumer НЕ бросает исключение наверх (partial failure поглощается)
        Событие НЕ попадает в DLT
```

**Метод:** `shouldDeliverToAvailableChannel_whenOneProviderFails`

---

### PF2 — Оба канала FAILED

```
given:  UserPreferences(userId="user-pf2", enabledChannels=[EMAIL, SMS])
        EmailProvider замокан → бросает MailException
        SmsProvider замокан → бросает RuntimeException
when:   событие обрабатывается
then:   2 записи в notifications: оба FAILED
        Событие НЕ уходит в DLT (orchestrator поглощает ошибки провайдеров)
        failed_notifications таблица пуста (DLT не вызывался)
```

**Метод:** `shouldMarkBothChannelsFailed_whenAllProvidersFail`

---

### E1 — Email не в payload

```
given:  UserPreferences(userId="user-e1", enabledChannels=[EMAIL])
        NotificationEvent(payload={}) — нет ключа "email"
when:   событие обрабатывается
then:   notifications WHERE channel=EMAIL → status=FAILED
        error_message = "SEND_FAILED:IllegalArgumentException"
```

**Метод:** `shouldFailNotification_whenEmailMissingFromPayload`

---

### E2 — payload = null → NPE

```
given:  UserPreferences(userId="user-e2", enabledChannels=[EMAIL])
        NotificationEvent(payload=null)
when:   событие обрабатывается
then:   notifications WHERE channel=EMAIL → status=FAILED
        error_message = "SEND_FAILED:NullPointerException"
```

> ⚠️ **Баг (E2-BUG):** `new HashMap<>(event.payload())` в `processChannel()` — если payload=null,
> бросает NullPointerException. Нет явной проверки. Фикс: `payload != null ? new HashMap<>(payload) : new HashMap<>()`.

**Метод:** `shouldFailNotification_whenPayloadIsNull`

---

### T1 — Шаблон не найден для известного eventType

```
given:  UserPreferences(userId="user-t1", enabledChannels=[EMAIL])
        NotificationEvent(eventType=PAYMENT_FAILED, payload={email: "t@t.com"})
        Файл templates/email/payment-failed.html НЕ существует
when:   событие обрабатывается
then:   notifications WHERE channel=EMAIL → status=FAILED
        error_message = "SEND_FAILED:TemplateNotFoundException"
```

**Метод:** `shouldFailNotification_whenTemplateNotFound`

---

### T2 — PUSH-канал всегда падает (нет директории templates/push/)

```
given:  UserPreferences(userId="user-push", enabledChannels=[PUSH])
        NotificationEvent(eventType=ORDER_COMPLETED, payload={})
        Директория templates/push/ отсутствует
when:   событие обрабатывается
then:   notifications WHERE channel=PUSH → status=FAILED
        error_message = "SEND_FAILED:TemplateNotFoundException"
```

> ⚠️ **Известное ограничение:** `PushProvider` реализован, но шаблоны для PUSH отсутствуют.
> PUSH-уведомления будут ВСЕГДА падать на этапе рендеринга.
> Фикс: создать `templates/push/{eventType}.txt` или добавить fallback в `TemplateService`.

**Метод:** `shouldFailPushNotification_whenNoPushTemplatesExist`

---

## RateLimitE2ETest

> Для изоляции тестов: каждый userId уникален (rate limit хранится в Redis per userId:channel).
> `notification.rate-limit.email.max-requests` в `application-test.yml` оставить = 5.

---

### R1 — Email rate limit превышен

```
given:  UserPreferences(userId="user-rl1", enabledChannels=[EMAIL])
        6 уникальных NotificationEvent с userId="user-rl1"
when:   все 6 отправлены в Kafka последовательно
then:   notifications WHERE user_id="user-rl1" AND channel=EMAIL:
        → 5 записей со status=DELIVERED
        → 1 запись со status=FAILED, error_message="RATE_LIMIT_EXCEEDED"
```

**Метод:** `shouldEnforceEmailRateLimit_whenLimitExceeded`

---

### R2 — Rate limit не пересекается между userId

```
given:  user-rl2a и user-rl2b — оба с enabledChannels=[EMAIL]
        По 5 событий для каждого
when:   10 событий отправлены
then:   user-rl2a: 5 × DELIVERED
        user-rl2b: 5 × DELIVERED
        Redis ключи ratelimit:user-rl2a:EMAIL и ratelimit:user-rl2b:EMAIL независимы
```

**Метод:** `shouldNotShareRateLimit_betweenDifferentUsers`

---

### R3 — Rate limit не пересекается между каналами

```
given:  UserPreferences(userId="user-rl3", enabledChannels=[EMAIL, SMS])
        6 событий (каждое генерирует 1 EMAIL + 1 SMS)
        email.max-requests=5, sms.max-requests=3
when:   6 событий отправлены
then:   EMAIL: 5 × DELIVERED, 1 × FAILED (RATE_LIMIT_EXCEEDED)
        SMS:   3 × DELIVERED, 3 × FAILED (RATE_LIMIT_EXCEEDED)
        Redis ключи ratelimit:user-rl3:EMAIL и ratelimit:user-rl3:SMS независимы
```

**Метод:** `shouldEnforceRateLimitPerChannel_independently`

---

## KafkaDltE2ETest

> `@RetryableTopic(attempts="3")` = 1 основная попытка + 2 retry.  
> Эти тесты требуют Testcontainers Kafka с реальными retry-топиками.

---

### K1 — Невалидный JSON → сразу DLT (NotRetryable)

```
given:  KafkaConfig.DefaultErrorHandler помечает SerializationException как NotRetryable
when:   В "notification.events" отправить raw String "not-a-json"
then:   Awaitility → failed_notifications таблица содержит 1 запись
        retry_count = 0 (не было retry — сразу DLT)
        error_message содержит информацию о SerializationException
```

**Метод:** `shouldGoDirectlyToDlt_whenMessageIsInvalidJson`

---

### K2 — RuntimeException → 3 попытки → DLT → failed_notifications

```
given:  UserPreferences(userId="user-k2", enabledChannels=[EMAIL])
        NotificationOrchestrator замокан → всегда бросает RuntimeException
        NotificationEvent(id="evt-k2", userId="user-k2")
when:   событие отправлено
then:   Awaitility (timeout 30s — нужно время на backoff) →
        failed_notifications содержит 1 запись:
          user_id="user-k2"
          error_message содержит "RuntimeException"
          retry_count = 0 (DLT handler не увеличивает retry_count)
        notification.events-retry-0 получил 1 сообщение
        notification.events-retry-1 получил 1 сообщение
        notification.events.dlt    получил 1 сообщение
```

**Метод:** `shouldSaveToDlt_afterExhaustingAllRetries`

---

### K3 — Успех на retry-1 (не доходит до DLT)

```
given:  UserPreferences(userId="user-k3", enabledChannels=[EMAIL])
        NotificationOrchestrator: первый вызов бросает RuntimeException,
                                  второй — успешен (AtomicInteger counter)
        NotificationEvent(id="evt-k3")
when:   событие отправлено
then:   Awaitility →
        notifications WHERE idempotency_key="evt-k3:EMAIL" → status=DELIVERED
        failed_notifications таблица пуста
        notification.events.dlt не получал сообщений
```

**Метод:** `shouldDeliverSuccessfully_whenRetrySucceeds`

---

## RetrySchedulerE2ETest

> В этих тестах `FailedNotificationRetryScheduler` НЕ замокан (переопределить через `@TestPropertySource`
> или вызывать `retryScheduler.retryFailedNotifications()` вручную).

---

### RS0 — Retry всегда падает из-за idempotency_key (🔴 критический баг)

```
given:  В notifications: idempotency_key="evt-rs0:EMAIL", status=FAILED (оригинальная попытка)
        В failed_notifications: userId="user-rs0", channel=EMAIL, status=PENDING,
          next_retry_at=past, eventPayload содержит id="evt-rs0"
        UserPreferences(userId="user-rs0", enabledChannels=[EMAIL])
        Provider работает нормально
when:   retryScheduler.retryFailedNotifications() вызван вручную
then:   ❌ ОЖИДАЕМОЕ поведение (после фикса):
          failed_notifications.status = DELIVERED
          notifications WHERE idempotency_key="evt-rs0:EMAIL" → status=DELIVERED
        ❌ ФАКТИЧЕСКОЕ поведение (баг):
          processChannel() пытается вставить Notification.pending() с тем же idempotency_key
          → DataIntegrityViolationException поймана → processRetry() возвращает false
          → failed_notifications.retry_count++ → после maxAttempts: status=FAILED
          Уведомление НИКОГДА не доставляется через retry
```

> 🔴 **RS-BUG (КРИТИЧЕСКИЙ):** `processRetry()` → `processChannel()` → `notificationRepository.save(Notification.pending(idempotencyKey))`
> → UNIQUE constraint на `idempotency_key` → `DataIntegrityViolationException` → возвращает `false`.
> Оригинальная запись с `status=FAILED` уже существует в `notifications` → повторная вставка невозможна.
> Retry механизм полностью нефункционален для любого уведомления, которое ранее обрабатывалось.
>
> **Фикс:** в `processRetry()` искать существующую запись через `findByIdempotencyKey()` и обновлять её status,
> а не создавать новую. Либо генерировать новый idempotencyKey для retry (`evt-id:EMAIL:retry-1`).

**Метод:** `shouldExposeBug_retryAlwaysFailsDueToIdempotencyKeyConstraint`

---

### RS1 — Retry успешен

```
given:  В failed_notifications: status=PENDING, next_retry_at=NOW()-1min, retry_count=0
        UserPreferences(userId того failed record, enabledChannels=[channel])
        Provider работает нормально
        ⚠️ Запись в notifications с тем же idempotency_key НЕ существует (чистое состояние)
when:   retryScheduler.retryFailedNotifications() вызван вручную
then:   failed_notifications.status = DELIVERED
        failed_notifications.last_retry_at IS NOT NULL
        В notifications появилась запись status=DELIVERED
```

> ℹ️ Этот тест пройдёт только после фикса RS-BUG, или если idempotency_key ещё не существует
> в таблице notifications (например, DLT сработал до сохранения основной записи).

**Метод:** `shouldDeliverNotification_whenRetrySucceeds`

---

### RS2 — Retry снова фейлится → exponential backoff

```
given:  failed_notifications: status=PENDING, next_retry_at=past, retry_count=1
        Provider замокан → бросает исключение
when:   retryScheduler.retryFailedNotifications() вызван
then:   failed_notifications.retry_count = 2
        failed_notifications.status = PENDING (ещё не исчерпан)
        next_retry_at ≈ NOW() + backoffBase * 2^(retry_count-1)
          (при backoffBase=5m и retry_count=2 → +10min)
```

**Метод:** `shouldIncrementRetryCount_whenRetryFails`

---

### RS3 — maxAttempts исчерпан → FAILED

```
given:  failed_notifications: status=PENDING, next_retry_at=past,
        retry_count = maxAttempts-1 (например 2 при maxAttempts=3)
        Provider замокан → бросает исключение
when:   retryScheduler.retryFailedNotifications() вызван
then:   failed_notifications.status = FAILED (окончательно)
        failed_notifications.retry_count = maxAttempts
        Логируется error "Retry exhausted"
```

**Метод:** `shouldMarkAsFailed_whenMaxAttemptsExhausted`

---

### RS4 — Канал удалён из preferences → retry не происходит

```
given:  failed_notifications: channel=EMAIL, status=PENDING, next_retry_at=past
        UserPreferences(userId, enabledChannels=[SMS]) — EMAIL убран
when:   retryScheduler.retryFailedNotifications() вызван
then:   failed_notifications.status остаётся PENDING
        retry_count НЕ увеличивается
        Логируется warn "Channel EMAIL is not enabled for user"
```

**Метод:** `shouldSkipRetry_whenChannelNoLongerEnabled`

---

## AnalyticsE2ETest

---

### A1 — Корректный расчёт rolling_7d_avg

```
given:  Вставить в notifications:
          - День 1: 10 total, 8 delivered → rate=80.0
          - День 2: 10 total, 9 delivered → rate=90.0
          - День 3: 10 total, 7 delivered → rate=70.0
        Все записи: channel=EMAIL, event_type=ORDER_COMPLETED
when:   GET /api/v1/analytics/delivery-stats?startDate=Day1&endDate=Day3+1
then:   200 OK, список из 3 элементов
        День 3: rolling_7d_avg = AVG(80, 90, 70) = 80.0
        День 2: rolling_7d_avg = AVG(80, 90) = 85.0
        День 1: rolling_7d_avg = 80.0
```

**Метод:** `shouldCalculateRolling7dAvg_whenMultipleDaysData`

---

### A2 — Пустой результат

```
given:  notifications таблица пуста
when:   GET /api/v1/analytics/delivery-stats?startDate=2026-01-01&endDate=2026-01-02
then:   200 OK, пустой список []
```

**Метод:** `shouldReturnEmptyList_whenNoNotifications`

---

### A3 — startDate >= endDate → 400

```
given:  startDate > endDate:
when:   GET /delivery-stats?startDate=2026-06-05&endDate=2026-06-01
then:   400 Bad Request, ProblemDetail { type: ".../invalid-date-range" }

given:  startDate == endDate:
when:   GET /delivery-stats?startDate=2026-06-05&endDate=2026-06-05
then:   400 Bad Request, ProblemDetail { type: ".../invalid-date-range" }
```

> ⚠️ Однодневный диапазон невозможен: `!startDate.isBefore(endDate)` бросает при равенстве.
> Минимальный диапазон: `startDate` и `endDate = startDate + 1 день`.

**Метод:** `shouldReturn400_whenStartDateNotBeforeEndDate`

---

### A4 — Redis cache hit (второй запрос не идёт в DB)

```
given:  notifications содержит данные
        @SpyBean на NotificationRepository
when:   GET /delivery-stats дважды с одинаковыми параметрами
then:   1-й запрос: Redis miss → DB → результат кэшируется
        2-й запрос: Redis hit → DB НЕ вызывается
        notificationRepository.findDeliveryStats вызван ровно 1 раз (verify через SpyBean)
```

**Метод:** `shouldReturnCachedResult_whenSameParamsRequested`

---

## ApiEdgeCaseTest

---

### API1 — Non-UUID в `GET /notifications/{id}/status` → 400

```
when:   GET /api/v1/notifications/not-a-uuid/status
then:   400 Bad Request
```

> ⚠️ **Баг (API1-BUG):** `MethodArgumentTypeMismatchException` не обработан в `GlobalExceptionHandler`.
> Сейчас упадёт в generic `@ExceptionHandler(Exception.class)` → 500.
> Фикс: добавить `@ExceptionHandler(MethodArgumentTypeMismatchException.class)` → 400.

**Метод:** `shouldReturn400_whenNotificationIdIsNotUuid`

---

### API2 — Отсутствующие params в analytics → 400

```
when:   GET /api/v1/analytics/delivery-stats (без startDate и endDate)
then:   400 Bad Request (MissingServletRequestParameterException → обработан ResponseEntityExceptionHandler)
        body: ProblemDetail { status: 400 }
```

**Метод:** `shouldReturn400_whenAnalyticsParamsMissing`

---

### API3 — PUT preferences с пустым enabledChannels → 400

```
when:   PUT /api/v1/users/user-1/preferences
        body: { "enabledChannels": [] }
then:   400 Bad Request (@NotEmpty нарушен → MethodArgumentNotValidException → обработан)
        body: ProblemDetail { type: ".../validation-failed", detail: "enabledChannels: must not be empty" }
```

**Метод:** `shouldReturn400_whenEnabledChannelsIsEmpty`

---

### API4 — GET preferences для несуществующего userId → 404

```
when:   GET /api/v1/users/unknown-user/preferences
then:   404 Not Found
        body: ProblemDetail { type: ".../not-found" }
```

**Метод:** `shouldReturn404_whenUserPreferencesNotFound`

---

### API5 — GET notification status для несуществующего id → 404

```
when:   GET /api/v1/notifications/00000000-0000-0000-0000-000000000000/status
then:   404 Not Found
        body: ProblemDetail { type: ".../not-found" }
```

**Метод:** `shouldReturn404_whenNotificationNotFound`

---

## Известные баги (требуют фикса или осознанного решения)

| ID | Компонент | Описание | Приоритет |
|----|-----------|----------|-----------|
| RS-BUG | `NotificationOrchestrator.processChannel()` | Retry всегда падает: UNIQUE constraint на idempotency_key при повторной вставке Notification.pending() | 🔴 |
| P1-BUG | `NotificationOrchestrator` | Dedup происходит ДО проверки preferences → событие помечается как дубликат даже если не обработано | 🔴 |
| P4-BUG | `NotificationOrchestrator` | `UserPreferences.enabled=false` не проверяется → уведомления отправляются даже отключённым пользователям | 🔴 |
| API3-BUG | `PreferenceService.upsert()` | Поле `enabled` никогда не обновляется через PUT → нельзя отключить пользователя через API | 🔴 |
| API1-BUG | `GlobalExceptionHandler` | `MethodArgumentTypeMismatchException` не обработан → non-UUID path variable возвращает 500 вместо 400 | 🟡 |
| E2-BUG | `NotificationOrchestrator` | `payload=null` → NullPointerException в `new HashMap<>(payload)` → нет явной проверки | 🟡 |
| T2-LIM | `TemplateService` | PUSH-канал: директория `templates/push/` отсутствует → все PUSH-уведомления FAILED | 🟡 |

---

## Инфраструктура тестов

### application-test.yml — специальные значения для тестов

```yaml
notification:
  rate-limit:
    email:
      max-requests: 5
      window: 1h
    sms:
      max-requests: 3
      window: 1h
  retry-scheduler:
    max-attempts: 3
    backoff-base: PT5M
    fixed-delay: PT1M
```

### Вспомогательные методы (добавить в тест-утилиты)

```java
// NotificationTestData — добавить:
static NotificationEvent eventWithId(String id, String userId, Channel channel, Map<String,String> payload)
static NotificationEvent eventWithNullPayload(String userId)
static NotificationEvent eventForRetry(String userId, Channel channel)

// PreferenceTestData — добавить:
static UserPreferences entityWith(String userId, Channel... channels)
```

### Awaitility (добавить в pom.xml если нет)

```xml
<dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <scope>test</scope>
</dependency>
```

---

## Известный потенциальный баг

**`UserPreferences.enabled` не проверяется в `NotificationOrchestrator`.**

`resolvePreferences()` проверяет только наличие preferences и непустоту `enabledChannels`,
но не проверяет `preferences.isEnabled()`. Если пользователь выставил `enabled=false`
через `PUT /api/v1/users/{id}/preferences` — уведомления всё равно отправляются.

**Решение:** добавить проверку или задокументировать как осознанное решение.
Тест для воспроизведения:

```
given:  UserPreferences(userId="user-disabled", enabledChannels=[EMAIL], enabled=false)
when:   NotificationEvent для user-disabled
then:   notifications пуста (или FAILED) — ожидаем; сейчас будет DELIVERED
```

---

## Итого

| Класс | Тестов |
|-------|--------|
| `NotificationPipelineE2ETest` | 13 |
| `RateLimitE2ETest` | 3 |
| `KafkaDltE2ETest` | 3 |
| `RetrySchedulerE2ETest` | 4 |
| `AnalyticsE2ETest` | 4 |
| `ApiEdgeCaseTest` | 5 |
| **Итого** | **32** |