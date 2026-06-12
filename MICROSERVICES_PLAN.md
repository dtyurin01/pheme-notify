# Microservices Plan — Pheme Notify

> План разбиения текущего сервиса на полноценную микросервисную систему.
> Цель — не «нарезать монолит на куски ради микросервисов», а показать **осознанный** распил: по границам бизнес-доменов, с правильными контрактами, изоляцией данных и реалистичной стратегией миграции.
>
> **Важно:** распил оправдан только когда есть реальные причины (независимый деплой/скейл команд, разные нагрузки/SLA). Этот документ явно фиксирует, **что выносим и почему** — а где монолит был бы правильнее.

---

## 0. Сначала — а нужно ли вообще?

Честный ответ для текущего масштаба: **монолит правильнее.** Один сервис уведомлений, одна команда, единый деплой. Микросервисы добавляют сетевые границы, distributed transactions, операционную сложность.

Поэтому этот план — **демонстрация зрелого мышления**: «я знаю, как распилить правильно, и знаю, когда этого делать НЕ нужно». На собеседовании это сильнее, чем слепой распил.

**Триггеры, при которых распил реально оправдан:**
- Разные команды владеют разными доменами и хотят деплоиться независимо.
- Резко разные профили нагрузки (рендеринг шаблонов CPU-bound vs аналитика I/O-bound).
- Разные требования к доступности (доставка — critical, аналитика — best-effort).
- Нужен polyglot (часть на другом стеке).

Ниже — как бы это сделали в настоящем проекте, **когда** триггеры наступили.

---

## 1. Границы доменов (Domain Boundaries)

Декомпозиция по **bounded context** (DDD), не по техническим слоям. Текущий монолит распадается на:

| Сервис | Ответственность | Почему отдельный |
|--------|-----------------|------------------|
| **ingestion-service** | Приём событий из Kafka, дедупликация (Redis SETNX), валидация, маршрутизация. Hard #1. | Точка входа, высокая throughput, отдельный скейл по партициям |
| **delivery-service** | Оркестрация доставки, rate limit (Hard #2), partial-failure logic, вызов провайдеров. | Ядро бизнес-логики, critical SLA |
| **provider-service(s)** | Адаптеры к внешним каналам: email, sms, push. Circuit Breaker. Можно по сервису на канал. | Изоляция сбоев провайдера, разные SLA/нагрузка/секреты per channel |
| **template-service** | Рендеринг Thymeleaf, версионирование шаблонов, локализация. | CPU-bound, кэшируемо, меняется независимо от доставки |
| **preference-service** | CRUD пользовательских настроек каналов. Source of truth для preferences. | Чистый CRUD-домен, своя БД, читается многими |
| **analytics-service** | Native SQL отчёты (Hard #3), Redis cache, дашборды. | I/O-bound, best-effort SLA, не должен влиять на доставку |

> Провайдеры можно начать как один `provider-service` с registry (как сейчас `ProviderRegistry`) и распилить per-channel позже, если у каналов разойдутся требования.

---

## 2. Контракты и связь между сервисами

### Async (предпочтительно — событийная связь через Kafka)

Основной паттерн — **event-driven choreography**. Сервисы общаются событиями, не зная друг о друге.

```
external → notification.events
              │
       [ingestion-service]  (dedup, validate)
              │ publishes
       notification.accepted
              │
       [delivery-service]   (rate limit, orchestrate)
              │ publishes per channel
       notification.dispatch.email / .sms / .push
              │
       [provider-service]   (send, circuit breaker)
              │ publishes
       notification.delivered / notification.failed
              │
       [analytics-service]  (consume, aggregate)
```

- Каждый шаг — отдельный топик, своя consumer group.
- `@RetryableTopic` + DLT сохраняются per-service.
- Дедупликация (Hard #1) живёт в ingestion, не размазана.

### Sync (REST — только где нужен немедленный ответ)

| Вызов | Кто → Кто | Зачем sync |
|-------|-----------|------------|
| Получить preferences | delivery → preference | Нужны до маршрутизации, нет смысла в событии |
| Статус уведомления | API gateway → delivery/analytics | Запрос пользователя, нужен ответ сразу |
| Render template | delivery → template | Результат нужен немедленно перед отправкой |

> Sync-вызовы — кандидаты на **gRPC** (типизированные контракты, быстрее JSON) или REST + OpenAPI. Защищать Circuit Breaker + timeout + fallback (Resilience4j, уже в проекте).

### Контракты как артефакты

- **Schema Registry** (Avro/Protobuf) для Kafka-событий — backward/forward compatibility, версионирование.
- **OpenAPI** spec для каждого REST API (springdoc уже есть) — генерация клиентов.
- **Consumer-Driven Contracts** (Spring Cloud Contract / Pact) — потребитель диктует контракт, ломающие изменения ловятся в CI.

---

## 3. Данные — Database per Service

Главный принцип микросервисов: **никакого shared database.** Каждый сервис владеет своими данными, чужие — только через API/события.

| Сервис | Хранилище | Данные |
|--------|-----------|--------|
| ingestion | Redis | dedup-ключи (TTL 24h) |
| delivery | PostgreSQL (своя БД) | `notifications` (статусы доставки), `failed_notifications` |
| preference | PostgreSQL (своя БД) | `user_preferences`, `user_preference_channels` |
| analytics | PostgreSQL (read-replica/OLAP) или ClickHouse | агрегаты, отчёты |
| template | PostgreSQL/S3 + Redis cache | версии шаблонов |
| provider | минимум state (логи попыток) | — |

### Проблема: распределённые данные

Аналитике (Hard #3) нужны данные о доставке из delivery-service. Решения:

- **Event sourcing / CDC**: delivery публикует `notification.delivered/failed`, analytics строит свою read-модель (CQRS). Это и сейчас естественно ложится на Kafka.
- **Не** делать JOIN между БД сервисов — это анти-паттерн (shared DB через чёрный ход).

### Saga вместо distributed transaction

Доставка по нескольким каналам — это **Saga** (а не 2PC). Текущая partial-failure logic уже почти Saga:
- Каждый канал — отдельный шаг.
- Компенсация — пометить статус FAILED, без отката других каналов.
- **Orchestration Saga**: delivery-service координирует и хранит state машину. (Choreography тоже возможна, но для доставки orchestration читаемее.)

---

## 4. Инфраструктура и cross-cutting

| Компонент | Решение | Зачем |
|-----------|---------|-------|
| **API Gateway** | Spring Cloud Gateway / Kong / Traefik | Единая точка входа, auth, rate limit на edge, маршрутизация |
| **Service Discovery** | K8s DNS / Consul / Eureka | Сервисы находят друг друга без хардкода адресов |
| **Config** | Spring Cloud Config / K8s ConfigMap+Secret | Централизованный конфиг, см. PRODUCTION_ROADMAP P1 |
| **Distributed tracing** | OpenTelemetry → Jaeger/Tempo | Трейс цепочки ingestion→delivery→provider (MDC correlation ID уже есть) |
| **Centralized logging** | Loki / ELK | Логи всех сервисов в одном месте по correlation ID |
| **Метрики** | Prometheus + Grafana (уже есть) per-service | Per-service дашборды, алерты |
| **Resilience** | Resilience4j (уже есть): CB, retry, timeout, bulkhead | Сбой одного сервиса не каскадит |
| **Идемпотентность** | Сохранить на каждой границе (Redis dedup, idempotency keys) | At-least-once Kafka → нужна защита от дублей |

> Многое из этого пересекается с PRODUCTION_ROADMAP.md — микросервисы делают prod-инфраструктуру **обязательной**, а не опциональной.

---

## 5. Стратегия миграции — Strangler Fig

Не переписывать всё с нуля (big-bang rewrite — главная ошибка). Душить монолит постепенно.

### M1 — Подготовка монолита (modular monolith)
Внутри текущего монолита навести чёткие модульные границы по доменам (раздел 1). Пакеты уже близки к этому. Убедиться, что модули общаются через интерфейсы, а не лезут в чужие таблицы. **Это самый ценный шаг** — даже если дальше не пойдём, код станет лучше.

### M2 — Вынести первый сервис (analytics)
Аналитика — идеальный первый кандидат: best-effort SLA, читает события, минимум обратных зависимостей. Поднять `analytics-service`, подписать на Kafka-события, построить свою read-модель. Монолит продолжает работать.

### M3 — Вынести preference-service
Чистый CRUD, своя БД. delivery начинает ходить в него по REST (с CB + fallback на кэш). Перенести таблицы `user_preferences*`.

### M4 — Разделить ingestion и delivery
Самое сложное — ядро. Ввести промежуточный топик `notification.accepted`. Сначала оба в монолите читают/пишут, потом физически разнести.

### M5 — Вынести provider-service(s)
delivery публикует `notification.dispatch.*`, провайдеры — отдельные consumers. Изолировать секреты провайдеров per-service.

### M6 — template-service + финал
Вынести рендеринг. Монолит «задушён» — остался тонкий delivery-orchestrator или исчез совсем.

> На каждом шаге: feature flag для переключения трафика, мониторинг, возможность откатить. Никогда не ломать прод.

---

## 6. Чеклист «правильные микросервисы» (не карго-культ)

- [ ] Распил по bounded context, не по слоям (controller/service/repo НЕ микросервисы).
- [ ] Database per service, ноль shared DB, ноль cross-DB JOIN.
- [ ] Async-first (Kafka events), sync только где нужен немедленный ответ.
- [ ] Версионируемые контракты (Schema Registry + OpenAPI + CDC-тесты).
- [ ] Saga вместо distributed transactions.
- [ ] Идемпотентность на каждой границе (at-least-once доставка).
- [ ] Distributed tracing + centralized logging + per-service метрики.
- [ ] Resilience: CB, timeout, retry, bulkhead на каждом sync-вызове.
- [ ] Independent deployability — каждый сервис деплоится отдельно.
- [ ] Strangler Fig миграция, не big-bang rewrite.
- [ ] Явно зафиксировано, **почему** распил оправдан (или почему пока нет).

---

## 7. Что показать на собеседовании

1. **Этот документ** — доказательство, что понимаешь и распил, и его цену.
2. **Modular monolith (M1)** — реально сделать в текущем проекте, низкий риск, высокий сигнал.
3. **Опционально вынести analytics (M2)** — один реальный микросервис как демо event-driven + CQRS.
4. Уметь объяснить: «я НЕ распилил всё, потому что для текущего масштаба монолит правильнее — вот триггеры, при которых я бы это сделал».

> Сильнейший сигнал зрелости — не количество сервисов, а **обоснованность** решения о границах.