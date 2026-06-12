# Production Roadmap — Pheme Notify

> Дорожная карта вывода проекта из dev-сетапа (localhost + docker-compose) в полноценный production.
> Разбита на спринты. Каждый спринт — самодостаточный блок: можно остановиться после любого и иметь рабочий, более защищённый сетап.
>
> **Текущее состояние (dev):** всё в одном `docker-compose.yml`, порты на `127.0.0.1`, секреты в `.env`, app запускается из IDE. Это нормально для портфолио/локальной разработки. Roadmap ниже — что добавить при реальном деплое.

---

## Принципы

1. **Не ломать dev.** Prod-конфиг живёт в отдельных файлах/профилях, dev-сетап с localhost остаётся как есть.
2. **Поэтапность.** Каждый спринт даёт измеримое улучшение. Не пытаться сделать всё сразу.
3. **Fail-fast и наблюдаемость.** Невалидный конфиг падает при старте, а не в рантайме. Всё логируется и метрится.
4. **Least privilege.** Сервисы видят только то, что им нужно. Никаких публичных портов БД/брокера.

---

## Sprint P1 — Конфигурация и секреты (фундамент)

**Цель:** разделить dev/prod конфиг, убрать секреты из репозитория.

| Задача | Что сделать | Критерий готовности |
|--------|-------------|---------------------|
| `application-prod.yml` | Отдельный профиль. Никаких дефолтов из `.env` — все значения через env vars `${VAR}` без fallback. | Старт с `SPRING_PROFILES_ACTIVE=prod` без переменных → fail-fast |
| Secret manager | Вынести секреты (PG/Redis/SMTP/Grafana пароли) из `.env` в Vault / AWS Secrets Manager / Doppler / SOPS. | `.env` нет на prod-хостах, секреты инжектятся при деплое |
| Валидация конфига | Проверить что все `@ConfigurationProperties` имеют `@Validated` (CLAUDE.md §14). | Невалидный конфиг → ошибка старта |
| `docker-compose.prod.yml` | Отдельный compose (override): без публикации внутренних портов, только reverse proxy наружу. | `docker compose -f docker-compose.yml -f docker-compose.prod.yml` поднимает prod-вариант |

**Результат:** конфиг разделён, секреты не в git, prod падает при неполном конфиге.

---

## Sprint P2 — Сеть и изоляция

**Цель:** убрать публичные порты инфраструктуры, изолировать сервисы в приватную сеть.

| Задача | Что сделать | Критерий готовности |
|--------|-------------|---------------------|
| Internal network | Создать Docker network (`internal: true` для backend-сети). PG/Redis/Kafka **без** `ports:` — доступны только внутри сети по DNS-имени. | `psql` снаружи хоста не подключается к БД |
| Reverse proxy | nginx / Traefik / Caddy перед app. Только 80/443 наружу. | Все HTTP-запросы идут через proxy |
| TLS | Let's Encrypt (Traefik/Caddy авто) или свои сертификаты. HTTP→HTTPS redirect. | `https://api.pheme.com` валидный сертификат |
| Закрыть UI-сервисы | Grafana/Prometheus/Kafka UI — за auth-прокси или VPN, не публично. | Прямой доступ к `:3000`/`:9090` снаружи закрыт |

**Результат:** наружу торчит только 443 через proxy, инфра в приватной сети.

---

## Sprint P3 — Managed-сервисы / надёжность данных

**Цель:** вынести stateful-компоненты из compose в managed или отказоустойчивый сетап.

| Задача | Что сделать | Критерий готовности |
|--------|-------------|---------------------|
| PostgreSQL | Managed (RDS / Cloud SQL / Supabase) или отдельный хост с репликой + бэкапами. Connection pool (HikariCP) настроен под нагрузку. | Автобэкапы включены, PITR доступен |
| Redis | Managed (ElastiCache / Upstash) или Redis с persistence + репликой. | Failover проверен |
| Kafka | Managed (Confluent Cloud / MSK / Redpanda Cloud) или кластер 3+ брокера. `replication.factor >= 3`, `min.insync.replicas=2`. | Топики переживают падение брокера |
| Миграции | Flyway запускается контролируемо при деплое (не авто на старте каждого инстанса) — отдельный job или `baseline-on-migrate` стратегия. | Нет гонки миграций при scale-out |

**Результат:** данные переживают падение узла, есть бэкапы.

---

## Sprint P4 — Деплой и масштабирование

**Цель:** воспроизводимый деплой, горизонтальное масштабирование app.

| Задача | Что сделать | Критерий готовности |
|--------|-------------|---------------------|
| Образ app | Multi-stage Dockerfile, non-root user, pinned base image + digest, минимальный runtime (distroless/jre-slim). | `docker scout` без критичных CVE |
| Оркестрация | Kubernetes / ECS / Nomad. Несколько реплик app за LB. Kafka consumer group масштабируется по партициям. | `kubectl scale` работает, нагрузка распределяется |
| Health/readiness | `/actuator/health/liveness` и `/readiness` (Spring Boot). LB снимает нездоровый инстанс. | Rolling deploy без downtime |
| Graceful shutdown | `server.shutdown=graceful`, Kafka дочитывает in-flight перед остановкой (уже частично в проекте). | SIGTERM → нет потерянных сообщений |
| CI/CD | Pipeline: test → build image → push registry → deploy. Сейчас есть `.github/`. | Push в main → авто-деплой на staging |

**Результат:** деплой одной командой/пушем, app масштабируется горизонтально.

---

## Sprint P5 — Observability в проде

**Цель:** видеть что происходит, получать алерты до того как заметят пользователи.

| Задача | Что сделать | Критерий готовности |
|--------|-------------|---------------------|
| Метрики | Prometheus managed (Grafana Cloud / AMP) скрейпит app. Дашборды из CLAUDE.md §9 (sent/duration/CB state/consumer lag). | Дашборд показывает live-трафик |
| Алерты | Alertmanager: consumer lag растёт, CB OPEN, error rate > X%, DLT не пустой. | Алерт приходит в Slack/PagerDuty |
| Логи | Централизованный сбор (Loki / ELK / CloudWatch). MDC correlation ID (уже есть) для трейсинга инцидента. | Поиск по `eventId`/`userId` через UI |
| Tracing | OpenTelemetry: Kafka consume → dedup → rate limit → provider. | Полный span цепочки в Jaeger/Tempo |

**Результат:** инцидент виден и алертит раньше, чем доходит до пользователя.

---

## Sprint P6 — Безопасность и hardening

**Цель:** закрыть оставшиеся векторы, привести к security-baseline.

| Задача | Что сделать | Критерий готовности |
|--------|-------------|---------------------|
| Auth на API | API больше не открытый. JWT / API key / OAuth2 на `/api/v1/**`. | Запрос без токена → 401 |
| Закрыть утечки | Разобрать `security-issues.md` (userId leak и пр.). | Открытые пункты закрыты |
| Rate limit на edge | Помимо per-user (Hard #2), глобальный rate limit на proxy уровне (anti-DDoS). | Burst с одного IP отбивается на proxy |
| Secrets rotation | Ротация паролей БД/ключей по расписанию. | Ротация не требует даунтайма |
| Image scanning | Trivy / Snyk в CI, fail на критичных CVE. | PR с уязвимым образом не мержится |
| Network policy | K8s NetworkPolicy / security groups: app ходит только в PG/Redis/Kafka, больше никуда. | Egress ограничен |

**Результат:** API защищён, секреты ротируются, образы сканируются.

---

## Чеклист «можно в прод»

- [ ] P1: prod-профиль, секреты вне git, fail-fast конфиг
- [ ] P2: приватная сеть, reverse proxy + TLS, UI закрыты
- [ ] P3: managed/HA для PG/Redis/Kafka, бэкапы, контролируемые миграции
- [ ] P4: воспроизводимый деплой, health/readiness, graceful shutdown, CI/CD
- [ ] P5: метрики + алерты + централизованные логи + tracing
- [ ] P6: auth на API, закрытые security-issues, scanning, rotation

---

## Что НЕ нужно для портфолио

Если цель — собеседование уровня Middle, полный P1-P6 избыточен. Достаточно:
- Показать, что **понимаешь** разницу dev/prod (этот файл — артефакт).
- Сделать P1 (prod-профиль) + P2 (TLS + proxy) как демо.
- Остальное описать в README как «production considerations».

Реальные P3-P6 имеют смысл только при реальном деплое с реальным трафиком.