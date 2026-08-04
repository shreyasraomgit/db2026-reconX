# Demo — run commands

Run these in order. Each numbered block is its own terminal (leave it running).

## 1. Infra (Postgres + Kafka + Prometheus + Grafana)

```bash
docker compose --profile debug up -d postgres kafka zookeeper prometheus grafana kafdrop
```

## 2. Backend (dev profile)

```bash
cd backend/api
../mvnw spring-boot:run "-Dspring-boot.run.profiles=dev" "-Dspring-boot.run.jvmArguments=-Dserver.port=8081"
```

> **PowerShell:** the `-D...` flags must be quoted like above (each whole
> `-Dkey=value` wrapped in `"..."`) or PowerShell mangles them and Maven
> throws an "invalid profile" error.

Backend is ready when this returns `{"status":"UP"}`:

```bash
curl -s http://localhost:8081/api/actuator/health
```

## 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173**

---

## Login (demo users)

| Role          | Email             | Password    |
|---------------|-------------------|-------------|
| ADMIN         | admin@db.com      | admin123    |
| TRADER        | trader@db.com     | trader123   |
| VIEWER        | viewer@db.com     | viewer123   |
| RECON_ANALYST | recon@db.com      | recon123    |

---

## Open once running

| What | Link | Day |
|---|---|---|
| React app | http://localhost:5173 — login, Dashboard, Trades, Add Trade, live SSE feed | 8–9 |
| Static dashboard | `static-dashboard/dashboard.html` — vanilla HTML/CSS/JS, no build step | 7 |
| Static trades table | `static-dashboard/trades.html` — sortable/resizable columns | 7 |
| Static recon page | `static-dashboard/recon.html` — trigger a run, resolve a break | 7 |
| Swagger API docs | http://localhost:8081/api/swagger-ui/index.html — every REST endpoint, try-it-out | 5 |
| H2 console | http://localhost:8081/api/h2 — JDBC URL `jdbc:h2:mem:reconx`, user `sa`, no password | 1 |
| Grafana | http://localhost:3000 — login admin/admin | 6 |
| Prometheus | http://localhost:9090 | 6 |
| Kafdrop | http://localhost:9000 — browse Kafka topics/messages | 9 |

---

## Demo script — day by day

Walk it in this order. Everything below is real, working functionality —
nothing faked. Two honest callouts are marked ⚠️ where you should narrate
rather than pretend it's automatic.

**Day 1 — PostgreSQL + Liquibase**
Open the H2 console (link above), connect, and show the tables Liquibase
built: `trades`, `instruments`, `counterparties`, `recon_breaks`, `revinfo`,
`trades_aud`, `users`. Point out this ran automatically on boot — no manual
schema step.

**Day 2 — Java OOP / sealed classes**
Quick code walkthrough (not a runtime demo): `backend/src/main/java/.../model/TradeType.java`
— the sealed hierarchy (`EquityTrade`, `FXTrade`, `BondTrade`, `DerivativeTrade`)
and the Builder pattern.

**Day 3 — Testing**
```bash
cd backend && ./mvnw test
```
Show the green run — `ReconciliationEngineTest` exercising the match/break logic.

**Day 4 — Enterprise setup / Envers audit**
In the app, PATCH a trade's status (or use Swagger's try-it-out on
`PATCH /v1/trades/{id}/status`). Then in the H2 console:
```sql
SELECT * FROM trades_aud WHERE id = 1;
SELECT * FROM revinfo;
```
Every change shows up as its own revision row — that's Hibernate Envers,
automatic, no code in the request path writes these.

**Day 5 — REST + JWT + RBAC**
Open Swagger UI. Log in as each of the 4 users (top of this file) and show:
- VIEWER can `GET /v1/trades` but a `POST` gets 403.
- TRADER can `POST`/`PATCH` trades but a recon endpoint gets 403.
- RECON_ANALYST/ADMIN can hit `/v1/recon/**` and `/v1/audit/**`.
- `POST /v1/recon/run` returns 202 + a jobId immediately (not a blocking 200).

**Day 6 — Observability**
```bash
curl -s http://localhost:8081/api/actuator/prometheus | grep trade_created_total
```
Then show the same metric ticking up in Grafana as you create trades.
⚠️ *Skip the reconciliation-duration panel* — it only populates when the
recon engine is actually invoked, which nothing in the live app does yet
(only its unit test calls it), so that panel will read "No data."

**Day 7 — static dashboard**
Open `static-dashboard/dashboard.html`, `trades.html`, `recon.html` directly.
Toggle dark/light theme, show the sortable/resizable trades table.

**Day 8–9 — React + Kafka**
Open the React app (localhost:5173). Log in, go to **Add Trade**, submit one.
Immediately show:
- **Dashboard** updating live with no refresh (the SSE feed).
- **Kafdrop** → `trade-events` topic → three consumer groups (`recon-service`,
  `audit-service`, `alert-service`) all showing lag 0 — the fan-out story.
- `PUT /v1/trades/{id}/status` (Swagger or curl) to move it to `MATCHED` —
  narrate this as "ops/recon confirms the match." ⚠️ *This step is manual*:
  nothing currently auto-flips a trade's status off `PENDING` — the Kafka
  consumer that would trigger that is intentionally left as a logging stub
  in the training material itself.
- To show the **resolve-a-break** flow (`PUT /v1/recon/results/{id}/resolve`),
  seed one row first via H2 console since nothing auto-creates breaks yet:
  ```sql
  INSERT INTO recon_breaks (trade_id, discrepancy_type, status, detected_at)
  VALUES (1, 'PRICE_MISMATCH', 'OPEN', NOW());
  ```
  Then `GET /v1/recon/jobs/anything/results` (as RECON_ANALYST) shows it,
  and `PUT /v1/recon/results/{id}/resolve` with a note resolves it.

---

## Shut down after the demo

```bash
docker compose --profile debug down
```
(Ctrl+C in the backend and frontend terminals to stop those.)
