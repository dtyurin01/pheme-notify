#!/bin/bash
# Generates ~2 minutes of varied traffic so every Grafana panel has data:
# sent/failed/duration, dedup, rate limit, DLT, retry outcomes, circuit breaker, PUSH channel.
# Run, then open Grafana with a 5-15min window and auto-refresh.
set -euo pipefail

KAFKA=pheme-kafka
APP=pheme-notify
MAILPIT=pheme-mailpit
TOPIC=notification.events
APP_URL=http://localhost:8080

send() {
  echo "$1" | docker exec -i "$KAFKA" kafka-console-producer \
    --bootstrap-server localhost:9092 --topic "$TOPIC" > /dev/null
}

now() { date -u +"%Y-%m-%dT%H:%M:%SZ"; }

event() {
  local id=$1 user=$2 type=$3 channel=$4
  printf '{"id":"%s","userId":"%s","eventType":"%s","channel":"%s","payload":{"orderId":"123","email":"%s@example.com"},"occurredAt":"%s"}' \
    "$id" "$user" "$type" "$channel" "$user" "$(now)"
}

put_prefs() {
  local user=$1 channels=$2
  curl -s -X PUT "$APP_URL/api/v1/users/$user/preferences" \
    -H 'Content-Type: application/json' \
    -d "{\"enabledChannels\":$channels,\"locale\":\"en\",\"timezone\":\"UTC\",\"enabled\":true}" \
    -o /dev/null -w "  prefs $user -> %{http_code}\n"
}

echo "== Setting up preferences (incl. PUSH user) =="
put_prefs user-1 '["EMAIL"]'
put_prefs user-2 '["SMS"]'
put_prefs user-3 '["EMAIL"]'
put_prefs user-4 '["EMAIL"]'
put_prefs user-5 '["PUSH"]'
put_prefs user-9 '["EMAIL"]'
put_prefs user-10 '["EMAIL"]'

echo "== Clearing rate-limit keys for a clean window =="
docker exec "$KAFKA" true >/dev/null 2>&1 || true
docker exec pheme-redis redis-cli --scan --pattern 'ratelimit:*' | xargs -r docker exec -i pheme-redis redis-cli del >/dev/null

RUN_ID=$(date +%s)

echo "== Phase 1: ~80s of steady mixed traffic (sent/failed/duration/dedup/PUSH) =="
for i in $(seq 1 16); do
  send "$(event "$RUN_ID-order-$i" "user-1" "ORDER_COMPLETED" "EMAIL")"
  send "$(event "$RUN_ID-reg-$i"   "user-2" "USER_REGISTERED" "SMS")"
  send "$(event "$RUN_ID-pay-$i"   "user-3" "PAYMENT_FAILED"  "EMAIL")"
  send "$(event "$RUN_ID-push-$i"  "user-5" "ORDER_COMPLETED" "PUSH")"
  sleep 5
done

echo "== Phase 2: duplicate event (dedup metric) =="
send "$(event "$RUN_ID-dup-1" "user-1" "ORDER_COMPLETED" "EMAIL")"
send "$(event "$RUN_ID-dup-1" "user-1" "ORDER_COMPLETED" "EMAIL")"

echo "== Phase 3: burst to trigger rate limit (email=5/hour, sms=3/hour) =="
for i in $(seq 1 8); do
  send "$(event "$RUN_ID-burst-email-$i" "user-4" "ORDER_COMPLETED" "EMAIL")"
  send "$(event "$RUN_ID-burst-sms-$i"   "user-2" "USER_REGISTERED" "SMS")"
done

echo "== Phase 4: poison message -> non-retryable -> DLT =="
echo '{not-valid-json' | docker exec -i "$KAFKA" kafka-console-producer \
  --bootstrap-server localhost:9092 --topic "$TOPIC" > /dev/null

echo "== Phase 5: stop Mailpit, send 10 EMAILs (fresh user-9/user-10) to trip the circuit breaker =="
docker stop "$MAILPIT" >/dev/null
for i in $(seq 1 5); do
  send "$(event "$RUN_ID-cb9-$i"  "user-9"  "ORDER_COMPLETED" "EMAIL")"
  send "$(event "$RUN_ID-cb10-$i" "user-10" "ORDER_COMPLETED" "EMAIL")"
  sleep 1
done

echo "== Waiting 35s (CB stays OPEN), then restarting Mailpit =="
sleep 35
docker start "$MAILPIT" >/dev/null

echo "== Phase 6: a bit more steady traffic so rate() graphs stay non-zero =="
for i in $(seq 17 24); do
  send "$(event "$RUN_ID-order-$i" "user-1" "ORDER_COMPLETED" "EMAIL")"
  send "$(event "$RUN_ID-reg-$i"   "user-2" "USER_REGISTERED" "SMS")"
  send "$(event "$RUN_ID-push-$i"  "user-5" "ORDER_COMPLETED" "PUSH")"
  sleep 5
done

echo "Done. Open Grafana, set range to 'Last 15 minutes', refresh every 5s."