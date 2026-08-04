#!/usr/bin/env bash
# ============================================================================
# File: scripts/smoke-test.sh
# TICKET-ADV153 — End-to-end smoke test for the full 7-service stack
# Run from repo root: bash scripts/smoke-test.sh
#
# Seven checks: stack up, login, trade POST, Kafka event consumed, Postgres
# audit row, Prometheus target UP, Grafana datasource reachable.
# Requires: curl, jq, docker compose.
# ============================================================================
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
GRAFANA_URL="${GRAFANA_URL:-http://localhost:3000}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
TRADE_REF="SMK-$(date +%Y%m%d)-$(printf '%04d' $((RANDOM % 9999)))"

echo "1/7  Bringing the stack up..."
docker compose down -v >/dev/null 2>&1 || true
docker compose up -d --build
echo "  Waiting up to 90s for backend to be healthy..."
status="starting"
for i in $(seq 1 18); do
  status=$(docker inspect --format='{{.State.Health.Status}}' reconx-backend 2>/dev/null || echo starting)
  [[ "$status" == "healthy" ]] && break
  sleep 5
done
[[ "$status" == "healthy" ]] || { echo "[step 1] stack up FAILED — backend not healthy"; exit 1; }
echo "  OK backend healthy"

echo "2/7  Logging in as trader..."
TOKEN=$(curl -fsS -X POST "$BASE_URL/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"trader@db.com","password":"trader123"}' | jq -r .token)
[[ -n "$TOKEN" && "$TOKEN" != "null" ]] || { echo "[step 2] login FAILED"; exit 1; }
echo "  OK JWT acquired"

echo "3/7  Posting a trade ($TRADE_REF)..."
TRADE=$(curl -fsS -X POST "$BASE_URL/api/v1/trades" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"tradeRef\":\"$TRADE_REF\",\"instrumentId\":1,\"counterpartyId\":1,\"assetClass\":\"EQUITY\",\"side\":\"BUY\",\"quantity\":100,\"price\":245.5,\"tradeDate\":\"2026-06-02\"}")
TRADE_ID=$(echo "$TRADE" | jq -r .id)
[[ -n "$TRADE_ID" && "$TRADE_ID" != "null" ]] || { echo "[step 3] trade post FAILED — $TRADE"; exit 1; }
echo "  OK trade created: id=$TRADE_ID"

echo "4/7  Confirming Kafka event..."
sleep 3
docker exec reconx-kafka kafka-console-consumer \
  --bootstrap-server kafka:29092 --topic trade-events \
  --from-beginning --max-messages 1 --timeout-ms 15000 2>/dev/null | grep -q "$TRADE_REF" \
  && echo "  OK trade-event found on topic" || { echo "[step 4] no Kafka event FAILED"; exit 1; }

echo "5/7  Confirming Postgres audit row..."
# On a freshly created topic the audit-service consumer group can take a
# few rebalance rounds to own every partition, so this retries generously
# rather than assuming the assignment settled instantly.
ok=0
for i in $(seq 1 20); do
  count=$(docker exec reconx-postgres psql -U reconx_user -d reconx -tAc \
    "SELECT COUNT(*) FROM audit_log WHERE trade_ref='$TRADE_REF';" | tr -d '[:space:]')
  [[ "$count" -gt 0 ]] && { ok=1; break; }
  sleep 3
done
[[ "$ok" -eq 1 ]] || { echo "[step 5] no audit row FAILED"; exit 1; }
echo "  OK audit row present"

echo "6/7  Confirming Prometheus scrape..."
curl -fsS "$PROMETHEUS_URL/api/v1/query" --data-urlencode 'query=up{job="reconx-backend"}' \
  | jq -e '.data.result[0].value[1]=="1"' >/dev/null \
  && echo "  OK Prometheus scraping backend" || { echo "[step 6] Prometheus target DOWN FAILED"; exit 1; }

echo "7/7  Confirming Grafana datasource..."
curl -fsS -u admin:admin "$GRAFANA_URL/api/datasources/uid/reconx-prometheus" \
  | jq -e '.uid=="reconx-prometheus"' >/dev/null \
  && echo "  OK Grafana datasource provisioned" || { echo "[step 7] Grafana datasource missing FAILED"; exit 1; }

echo
echo "All 7 checks green - stack is demo-ready."
