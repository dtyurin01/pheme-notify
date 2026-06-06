# Pheme Notify — Application Flow

> Подробное описание того, как обрабатывается уведомление: от входящего Kafka-события до записи в базу данных.

---

## Общая схема

```
Kafka event
    │
    ▼
NotificationEventConsumer       ← @RetryableTopic (3x, backoff 5s/10s/20s)
    │
    ▼
NotificationOrchestrator
    │
    ├─► RedisDeduplicationAdapter   → Redis SETNX "dedup:{id}" TTL=24h
    │       дубликат? → STOP
    │
    ├─► UserPreferenceRepository    → PostgreSQL: какие каналы включены
    │       нет настроек? → STOP
    │
    └─► для каждого канала (partial failure — каналы независимы):
            │
            ├─► NotificationRepository.save(PENDING)
            ├─► RedisRateLimitAdapter        → Lua sliding window
            │       превышен? → FAILED, следующий канал
            ├─► TemplateService              → Thymeleaf render
            ├─► ProviderRegistry.getProvider(channel)
            │       EmailProvider            → Circuit Breaker → JavaMailSender → Mailpit
            │       SmsProvider             → мок (лог + sleep)
            └─► NotificationRepository.save(DELIVERED / FAILED)

если все retry провалились:
    DLT → handleDlt() → failed_notifications (JSONB)
              ▲
              │ каждые N секунд
    FailedNotificationRetryScheduler → processRetry()
```

---

## Шаг 1 — Входящее Kafka-событие

**Файл:** `src/main/java/com/pheme/phemenotify/messaging/consumer/NotificationEventConsumer.java`

Kafka-топик `notification.events` принимает события трёх типов:
- `order.completed`
- `user.registered`
- `payment.failed`

Каждое событие десериализуется в `NotificationEvent` record:

```java
public record NotificationEvent(
    String id,           // UUID — ключ дедупликации
    String userId,
    EventType eventType,
    Channel channel,
    Map<String, String> payload,
    Instant occurredAt
) {}
```

`@RetryableTopic` автоматически создаёт retry-топики и настраивает экспоненциальный backoff:

| Попытка | Топик | Задержка |
|---------|-------|----------|
| 1 | `notification.events` | — |
| 2 | `notification.events-retry-0` | 5 сек |
| 3 | `notification.events-retry-1` | 10 сек |
| DLT | `notification.events.dlt` | после 3-й неудачи |

---

## Шаг 2 — Оркестратор запускает pipeline

**Файл:** `src/main/java/com/pheme/phemenotify/service/NotificationOrchestrator.java`

Центральный сервис. Получает событие от consumer и последовательно вызывает все шаги.
Реализует паттерн **Partial Failure**: каналы обрабатываются в отдельных `try/catch` блоках.
Ошибка в EMAIL не останавливает SMS.

---

## Шаг 3 — Дедупликация (Hard #1)

**Файл:** `src/main/java/com/pheme/phemenotify/infrastructure/redis/RedisDeduplicationAdapter.java`

Redis-команда `SETNX` атомарно записывает ключ только если его нет:

```
SETNX dedup:{eventId} "1" EX 86400
```

| Результат SETNX | Значение | Действие |
|----------------|----------|----------|
| `true` | ключ не существовал → событие новое | продолжаем |
| `false` | ключ уже был → дубликат | пропускаем |

TTL = 24 часа. Если Kafka доставит одно событие дважды (at-least-once гарантия Kafka),
второй раз оно будет проигнорировано. Это обеспечивает **effectively exactly-once** семантику.

---

## Шаг 4 — Загрузка настроек пользователя

**Файл:** `src/main/java/com/pheme/phemenotify/persistence/repository/UserPreferenceRepository.java`

```sql
SELECT * FROM user_preferences WHERE user_id = :userId
```

Возвращает `UserPreferences` — список включённых каналов: например `[EMAIL, SMS]`.

- Настроек нет → `log.warn` → обработка останавливается
- Все каналы отключены → `log.warn` → обработка останавливается

---

## Шаг 5 — Обработка каждого канала

**Файл:** `src/main/java/com/pheme/phemenotify/service/NotificationOrchestrator.java` — метод `processChannel()`

Для каждого канала из `UserPreferences.enabledChannels` выполняются шаги 5а–5д.
Если канал завершился ошибкой — переходим к следующему каналу, не бросаем exception наверх.

---

### Шаг 5а — Сохранение записи PENDING

**Файл:** `src/main/java/com/pheme/phemenotify/persistence/repository/NotificationRepository.java`

До отправки сохраняем запись в таблицу `notifications` со статусом `PENDING`.

Ключ идемпотентности: `{eventId}:{channel}` — уникальный `UNIQUE` constraint в БД.
Если запись уже существует (`DataIntegrityViolationException`) → дубликат, пропускаем канал.
Это страхует от race condition при параллельных retry от Kafka.

---

### Шаг 5б — Rate Limit (Hard #2)

**Файлы:**
- `src/main/java/com/pheme/phemenotify/service/RateLimitService.java`
- `src/main/java/com/pheme/phemenotify/infrastructure/redis/RedisRateLimitAdapter.java`
- `src/main/resources/redis/rate_limit.lua`

`RedisRateLimitAdapter` выполняет Lua-скрипт **атомарно** через `redisTemplate.execute()`.
Ключ в Redis: `ratelimit:{userId}:{channel}`.

Lua-скрипт выполняет 4 операции в одной транзакции:

```lua
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)  -- удалить старые записи
local count = redis.call('ZCARD', key)                 -- посчитать за окно
if count >= max_requests then return 1 end             -- заблокировать
redis.call('ZADD', key, now, now .. ':' .. random)     -- добавить текущий
redis.call('PEXPIRE', key, window)                     -- обновить TTL
return 0                                               -- разрешить
```

Лимиты по каналам:

| Канал | Лимит | Окно |
|-------|-------|------|
| EMAIL | 5 | 1 час |
| SMS | 3 | 1 час |
| PUSH | 20 | 1 час |

Почему Lua, а не Java? Несколько Redis-команд из Java создают race condition:
между `ZCARD` и `ZADD` другой поток может вклиниться и нарушить счётчик.

При превышении: `status = FAILED`, `errorMessage = "RATE_LIMIT_EXCEEDED"`, переходим к следующему каналу.

---

### Шаг 5в — Рендеринг шаблона

**Файл:** `src/main/java/com/pheme/phemenotify/service/TemplateService.java`

Thymeleaf рендерит шаблон по пути `templates/{channel}/{eventType}`.

Примеры:
- `templates/email/order.completed.html`
- `templates/sms/user.registered.txt`

В шаблон передаётся `payload` из события — там могут быть имя пользователя,
сумма заказа, код верификации и т.д.

---

### Шаг 5г — Отправка через провайдер

**Файлы:**
- `src/main/java/com/pheme/phemenotify/provider/ProviderRegistry.java`
- `src/main/java/com/pheme/phemenotify/provider/EmailProvider.java`
- `src/main/java/com/pheme/phemenotify/provider/SmsProvider.java`

`ProviderRegistry` — это `Map<Channel, NotificationProvider>`. Паттерн **Strategy**:
по каналу достаём нужную реализацию.

**EmailProvider** оборачивает отправку в **Circuit Breaker** (Resilience4j):

| Состояние CB | Что происходит |
|-------------|---------------|
| `CLOSED` | письма идут через `JavaMailSender` → Mailpit (dev) |
| `OPEN` | вызовы сразу падают без попытки подключиться к SMTP |
| `HALF_OPEN` | после таймаута пропускает один тестовый запрос |

CB переходит в `OPEN` после N ошибок подряд — защищает от шторма запросов
к недоступному SMTP-серверу.

**SmsProvider** — мок: логирует сообщение и делает `Thread.sleep(random)` для
имитации реальной задержки внешнего API.

---

### Шаг 5д — Обновление статуса в БД

**Файл:** `src/main/java/com/pheme/phemenotify/persistence/repository/NotificationRepository.java`

| Результат | Статус | Поля |
|-----------|--------|------|
| Успех | `DELIVERED` | `sent_at = now()` |
| Ошибка | `FAILED` | `error_message = "SEND_FAILED:RuntimeException"` |

В `error_message` — нормализованный код, не `e.getMessage()`.
Детали исключения — только в логах (SLF4J автоматически пишет stacktrace).

---

## Шаг 6 — DLT: Dead Letter Topic

**Файл:** `src/main/java/com/pheme/phemenotify/messaging/consumer/NotificationEventConsumer.java` — метод `handleDlt()`

Если все 3 retry исчерпаны, Spring Kafka автоматически направляет сообщение
в `notification.events.dlt`. Метод `@DltHandler` получает событие и сохраняет
его в таблицу `failed_notifications` с полным `event_payload` в JSONB:

```json
{
  "id": "uuid",
  "userId": "user-123",
  "eventType": "order.completed",
  "channel": "EMAIL",
  "occurredAt": "2026-05-30T10:00:00Z",
  "payload": { "email": "user@example.com" }
}
```

JSONB позволяет полностью восстановить событие для повторной попытки.

---

## Шаг 7 — Scheduled Retry

**Файл:** `src/main/java/com/pheme/phemenotify/messaging/retry/FailedNotificationRetryScheduler.java`

`@Scheduled` запускается каждые N секунд (настраивается через `RetrySchedulerProperties`).
Запрашивает из `failed_notifications` записи где `status = PENDING` и `next_retry_at < now()`.

Экспоненциальный backoff:

| Попытка | Следующая попытка через |
|---------|------------------------|
| 1 | базовый интервал (например 1 мин) |
| 2 | ×2 |
| 3 | ×4 |
| N >= maxAttempts | статус `FAILED` навсегда |

Восстанавливает `NotificationEvent` из JSONB и вызывает
`NotificationOrchestrator.processRetry()` — тот же pipeline, но **без дедупликации**
(это уже не новое событие, dedup-шаг пропускается).

---

## Параллельные флоу — REST API

### Analytics API (Hard #3)

**Файлы:**
- `src/main/java/com/pheme/phemenotify/api/controller/AnalyticsController.java`
- `src/main/java/com/pheme/phemenotify/service/AnalyticsService.java`
- `src/main/java/com/pheme/phemenotify/persistence/repository/NotificationRepository.java`

```
GET /api/v1/analytics/delivery-stats?startDate=2026-05-01&endDate=2026-05-30
    │
    ▼
AnalyticsService
    │
    ├─► Redis cache (@Cacheable, ключ: "startDate:endDate", TTL=1h)
    │       cache hit  → вернуть сразу
    │       cache miss → выполнить SQL
    │
    └─► NotificationRepository.findDeliveryStats()
            нативный SQL:
            - DATE_TRUNC('day', created_at) — группировка по дням
            - COUNT(*) FILTER (WHERE status = 'DELIVERED') — статистика
            - AVG(delivery_rate) OVER (PARTITION BY channel, event_type
              ORDER BY day ROWS BETWEEN 6 PRECEDING AND CURRENT ROW)
              — скользящее 7-дневное среднее
```

### Preferences API

```
GET  /api/v1/users/{id}/preferences  → PreferenceController → PreferenceService → UserPreferenceRepository
PUT  /api/v1/users/{id}/preferences  → валидация → сохранить enabledChannels
```

### Notification Status API

```
GET /api/v1/notifications/{id}/status → NotificationController → NotificationRepository.findById()
```

---

## Обработка ошибок

**Файл:** `src/main/java/com/pheme/phemenotify/api/exception/GlobalExceptionHandler.java`

Все HTTP-ошибки возвращаются в формате **Problem Detail (RFC 9457)**:

```json
{
  "type": "https://pheme.com/errors/rate-limit-exceeded",
  "title": "Rate Limit Exceeded",
  "status": 429,
  "detail": "Email limit: 5/hour exceeded for user 123"
}
```

Stacktrace клиенту **никогда не возвращается**. Неожиданные ошибки логируются с UUID:
`log.error("Unexpected error [id={}]", errorId, exception)` — по UUID можно найти
в логах полный stacktrace.

---

## Конфигурация инфраструктуры

| Сервис | Порт | Назначение |
|--------|------|-----------|
| App (IDE) | 8081 | REST API + Actuator |
| Kafka | 9092 | Входящие события |
| Kafka UI | 8090 | Мониторинг топиков и сообщений |
| PostgreSQL | 55432 | Хранение notifications, preferences |
| Redis | 6379 | Dedup, Rate Limit, Analytics cache |
| Mailpit SMTP | 1025 | Перехват email в dev |
| Mailpit UI | 8025 | Просмотр отправленных писем |
| Prometheus | 9090 | Сбор метрик |
| Grafana | 3000 | Дашборды: sent/failed/duration/CB state |
