# Day 10 — Run Guide

One command boots the entire ReconX stack (database, message queue, backend,
frontend, and monitoring). This doc is for anyone who wants to turn it on and
click around — no prior context needed.

## Prerequisite

Docker Desktop, installed and running. That's it — everything else (Java,
Node, Postgres, Kafka) runs inside containers.

## 1. Start everything

From the repo root:

```bash
docker compose --profile debug up -d --build
```

`--profile debug` also brings up Kafdrop (the Kafka message browser) — leave
it off if you only want the core 7 services.

First run takes a few minutes (building images, downloading Postgres/Kafka/
Grafana). Check progress with:

```bash
docker compose ps
```

Wait until **every** row's STATUS column says `(healthy)` (or just `Up` for
services without a healthcheck — frontend, prometheus, grafana, zookeeper,
kafdrop). Usually takes 60–90 seconds from a cold start.

## 2. Open the app

**Important: use `127.0.0.1`, not `localhost`.** On this machine, `localhost`
resolves to IPv6 first, which triggers a Docker Desktop port-forwarding bug
that breaks API calls (you'll get HTTP 500s on login). `127.0.0.1` sidesteps
it entirely.

Go to **http://127.0.0.1:5173** and log in:

| Role | Email | Password | Can do |
|---|---|---|---|
| Trader | `trader@db.com` | `trader123` | Create/edit trades, update status |
| Admin | `admin@db.com` | `admin123` | Everything, including delete |
| Viewer | `viewer@db.com` | `viewer123` | Read-only |
| Recon Analyst | `recon@db.com` | `recon123` | Run reconciliation, resolve breaks, view audit trail |

Click around: **Add Trade** to create one, **Dashboard** to watch it appear
live (no refresh needed — that's a real-time feed), **Trades** to browse the
full list.

## 3. Everything else worth opening

| What | URL | Notes |
|---|---|---|
| **API docs (Swagger)** | http://127.0.0.1:8080/api/swagger-ui/index.html | See "Using Swagger" below |
| **Kafka message browser (Kafdrop)** | http://127.0.0.1:9000 | Click into the `trade-events` topic — every trade you create shows up here as a message |
| **Grafana dashboards** | http://127.0.0.1:3000 | Login `admin` / `admin` — pre-built "ReconX" dashboard, no setup needed |
| **Prometheus** | http://127.0.0.1:9090 | Raw metrics — mostly useful for confirming a target is `UP` under Status → Targets |
| **Static dashboard (plain HTML, no build step)** | open `static-dashboard/dashboard.html` as a local file | An earlier, framework-free version of the trades UI |

## 4. Using Swagger to call the API directly

1. Open http://127.0.0.1:8080/api/swagger-ui/index.html — it defaults to the
   **"all"** group, showing every endpoint (auth, trades, recon, audit).
2. Expand **`auth`** → `POST /auth/login` → **Try it out** → the body is
   pre-filled with a working example → **Execute**. Copy the `token` from
   the response.
3. Click the green **Authorize** button (top right), paste the token in,
   click Authorize, then Close. This sticks even if you refresh the page.
4. Every other endpoint now works from the UI — each one opens pre-filled
   with realistic example values, just hit **Execute**.

To mark a trade as matched: `trades` → `PATCH /v1/trades/{id}/status` →
put in the trade's id → body `{"status": "MATCHED"}` → Execute. Only
`trader`/`admin` logins can do this (`viewer` gets a 403).

## 5. Proving the whole pipeline works in one shot

```bash
bash scripts/smoke-test.sh
```

This tears the stack down, brings it back up clean, logs in, creates a
trade, confirms it landed on the Kafka topic, confirms an audit row was
written, and confirms Prometheus/Grafana are wired up — 7 checks, all green
means the whole system is demo-ready. Takes about 90 seconds. Requires
`curl` and `jq` on your PATH.

## 6. Load-testing it

```bash
k6 run loadtest/trade-creation.js
```

Simulates 200 concurrent users creating trades for 2 minutes and asserts
p95 latency under 800ms and error rate under 2%. Needs
[k6](https://k6.io/docs/get-started/installation/) installed, or run it via
Docker without installing anything:

```bash
docker run --rm -v "$(pwd)/loadtest:/loadtest" \
  -e BASE_URL=http://host.docker.internal:8080 \
  grafana/k6 run /loadtest/trade-creation.js
```

## 7. A good demo flow

Create a trade in the React app → watch it appear instantly on the
Dashboard (no refresh) → switch to Kafdrop and show the same event landing
on the `trade-events` topic → switch to Grafana and show the trade-count
metric tick up. One action rippling through four different systems.

## 8. Shutting down

```bash
docker compose down
```

Stops and removes all containers but keeps your data (trades, Grafana
dashboards) in named volumes. To wipe everything and start completely
fresh next time:

```bash
docker compose down -v
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| HTTP 500 on login via the browser | You're on `localhost` — switch to `127.0.0.1` |
| `docker compose up` fails with a port already in use | Something else on your machine is using that port (commonly 8080 or 9092) — find it with `netstat -ano \| findstr :8080` (Windows) and stop it, or change the port mapping in `docker-compose.yml` |
| A service stays stuck on `(health: starting)` | Give it another minute — Kafka and the backend both take 30–60s to fully boot. If it never clears, check `docker compose logs <service>` |
| Trades list is empty after a fresh `docker compose down -v` | Expected — that wipes the database. Create a few trades again via the app or Swagger |
