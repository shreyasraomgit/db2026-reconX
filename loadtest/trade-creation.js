// ============================================================================
// File: loadtest/trade-creation.js
// TICKET-ADV158 — k6 load test: 200 concurrent users posting trades for 2 min
// Run:  k6 run loadtest/trade-creation.js
//       BASE_URL=http://localhost:8080 k6 run loadtest/trade-creation.js
// ============================================================================
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const tradeLatency = new Trend('trade_post_latency_ms');
const errorRate    = new Rate('trade_post_errors');

export const options = {
  scenarios: {
    constant_load: {
      executor:     'constant-vus',
      vus:          200,
      duration:     '2m',
      gracefulStop: '10s',
    },
  },
  thresholds: {
    'trade_post_latency_ms': ['p(95)<800', 'p(99)<2000'],
    'trade_post_errors':     ['rate<0.02'],
    'http_req_failed':       ['rate<0.02'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Seeded reference data (backend/src/main/resources/db/changelog/changes/data)
const INSTRUMENT_IDS = [1, 2, 3, 4, 5];
const COUNTERPARTY_IDS = [1, 2, 3, 4, 5];

// One-time login per test run (all VUs share the token from setup())
export function setup() {
  const res = http.post(`${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: 'trader@db.com', password: 'trader123' }),
    { headers: { 'Content-Type': 'application/json' } });
  const token = res.json('token');
  if (!token) {
    throw new Error(`setup() login failed: status=${res.status} body=${res.body}`);
  }
  return { token };
}

// tradeRef must match ^[A-Z]{3}-\d{8}-\d{4}$ (see TradeRequest DTO). Each VU
// gets its own 3-letter prefix (base-26 encoding of __VU, up to 17576 VUs)
// so cross-VU collisions are impossible; the digit part is a millisecond
// timestamp slice, which only repeats for the SAME VU on an exact 10s
// wrap-around — far rarer than the fixed 0.5s sleep between iterations.
function vuPrefix(vu) {
  const letters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
  let n = vu, out = '';
  for (let i = 0; i < 3; i++) {
    out = letters[n % 26] + out;
    n = Math.floor(n / 26);
  }
  return out;
}

function tradeRef() {
  const today = '20260804';
  const seq = (Date.now() % 10000).toString().padStart(4, '0');
  return `${vuPrefix(__VU)}-${today}-${seq}`;
}

export default function (data) {
  const payload = JSON.stringify({
    tradeRef:        tradeRef(),
    instrumentId:    INSTRUMENT_IDS[__VU % INSTRUMENT_IDS.length],
    counterpartyId:  COUNTERPARTY_IDS[__VU % COUNTERPARTY_IDS.length],
    assetClass:      'EQUITY',
    side:            __ITER % 2 === 0 ? 'BUY' : 'SELL',
    quantity:        100 + (__VU % 50),
    price:            245.50 + (__ITER % 10) * 0.01,
    tradeDate:       '2026-06-02',
  });

  const t0 = Date.now();
  const res = http.post(`${BASE_URL}/api/v1/trades`, payload, {
    headers: {
      'Content-Type': 'application/json',
      Authorization:  `Bearer ${data.token}`,
    },
  });
  tradeLatency.add(Date.now() - t0);

  const ok = check(res, {
    '201 created':  r => r.status === 201,
    'has trade id': r => !!r.json('id'),
  });
  errorRate.add(!ok);

  sleep(0.5);
}
