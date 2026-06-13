#!/bin/bash
# Sends test events into notification.events to populate Grafana dashboard.
set -euo pipefail

CONTAINER=pheme-kafka
TOPIC=notification.events

send() {
  echo "$1" | docker exec -i "$CONTAINER" kafka-console-producer \
    --bootstrap-server localhost:9092 --topic "$TOPIC" > /dev/null
}

now() { date -u +"%Y-%m-%dT%H:%M:%SZ"; }
RUN_ID=$(date +%s)

event() {
  local id=$1 user=$2 type=$3 channel=$4
  printf '{"id":"%s","userId":"%s","eventType":"%s","channel":"%s","payload":{"orderId":"123"},"occurredAt":"%s"}' \
    "$id" "$user" "$type" "$channel" "$(now)"
}

echo "Sending normal events (sent/failed/duration metrics)..."
for i in $(seq 1 5); do
  send "$(event "$RUN_ID-order-$i" "user-1" "ORDER_COMPLETED" "EMAIL")"
  send "$(event "$RUN_ID-reg-$i" "user-2" "USER_REGISTERED" "SMS")"
  send "$(event "$RUN_ID-pay-$i" "user-3" "PAYMENT_FAILED" "EMAIL")"
  sleep 0.2
done

echo "Sending duplicate event (dedup metric)..."
send "$(event "$RUN_ID-dup-1" "user-1" "ORDER_COMPLETED" "EMAIL")"
send "$(event "$RUN_ID-dup-1" "user-1" "ORDER_COMPLETED" "EMAIL")"

echo "Sending burst to trigger rate limit (email=5/hour for user-4)..."
for i in $(seq 1 8); do
  send "$(event "$RUN_ID-burst-$i" "user-4" "ORDER_COMPLETED" "EMAIL")"
done

echo "Done. Check Grafana / Mailpit."
