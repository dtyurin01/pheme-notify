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
