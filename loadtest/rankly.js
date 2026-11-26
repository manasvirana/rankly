// Load test: many live viewers on one contest while scores are submitted at a fixed rate.
// Use a new, empty contest for every run.
//
// Settings (environment variables):
//   BASE_URL    e.g. https://example.org      CONTEST_ID  contest to test
//   VIEWERS     live WebSocket viewers (10)    RATE        score submissions per second (2)
//   DURATION    seconds of submissions (30)    RAMP        seconds to connect viewers (10)
//   PLAYERS     test accounts that submit (20)
import http from 'k6/http';
import ws from 'k6/ws';
import exec from 'k6/execution';
import { check, fail, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const WS_URL = BASE_URL.replace(/^http/, 'ws');
const CONTEST_ID = __ENV.CONTEST_ID;
const VIEWERS = Number(__ENV.VIEWERS || 10);
const RATE = Number(__ENV.RATE || 2);
const DURATION = Number(__ENV.DURATION || 30);
const RAMP = Number(__ENV.RAMP || 10);
const PLAYERS = Number(__ENV.PLAYERS || 20);

const viewersConnected = new Counter('viewers_connected');
const updatesReceived = new Counter('updates_received');
const submissionsOk = new Counter('submissions_ok');
const updateLatency = new Trend('update_latency', true);
const submitLatency = new Trend('submit_latency', true);

export const options = {
  setupTimeout: '180s',
  summaryTrendStats: ['med', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    viewers: {
      executor: 'per-vu-iterations',
      exec: 'viewer',
      vus: VIEWERS,
      iterations: 1,
      maxDuration: `${RAMP + DURATION + 60}s`,
    },
    submitters: {
      executor: 'constant-arrival-rate',
      exec: 'submit',
      startTime: `${RAMP + 5}s`, // begin once the viewers are connected
      rate: RATE,
      timeUnit: '1s',
      duration: `${DURATION}s`,
      preAllocatedVUs: 20,
      maxVUs: 200,
    },
  },
};

const json = { headers: { 'Content-Type': 'application/json' } };

// Runs once before timing starts, so password hashing doesn't distort the results.
export function setup() {
  if (!CONTEST_ID) fail('Set CONTEST_ID');
  const contest = http.get(`${BASE_URL}/api/v1/contests/${CONTEST_ID}`);
  if (contest.status !== 200) fail(`Contest not found (HTTP ${contest.status})`);
  const board = http.get(`${BASE_URL}/api/v1/contests/${CONTEST_ID}/leaderboard`);
  if (board.json().length > 0) fail('Use a new, empty contest for each run');

  const tag = Date.now().toString(36);
  const password = `${Math.random().toString(36).slice(2)}${Math.random().toString(36).slice(2)}`;
  const tokens = [];
  for (let i = 0; i < PLAYERS; i++) {
    const username = `lt_${tag}_${i}`;
    const body = JSON.stringify({ username, email: `${username}@example.com`, password });
    const res = http.post(`${BASE_URL}/api/v1/auth/register`, body, json);
    if (res.status !== 201) fail(`Registration failed (HTTP ${res.status})`);
    tokens.push(res.json('token'));
  }
  return { tokens, start: Date.now() };
}

// One viewer: connects, then measures how old each leaderboard update is when it arrives.
export function viewer(data) {
  const delay = Math.random() * RAMP;
  sleep(delay);
  const holdSeconds = RAMP - delay + DURATION + 10;

  const res = ws.connect(`${WS_URL}/ws/leaderboard/${CONTEST_ID}`, {}, (socket) => {
    socket.on('open', () => viewersConnected.add(1));
    socket.on('message', (message) => {
      const top = JSON.parse(message).topEntries[0];
      if (!top) return;
      updatesReceived.add(1);
      // The top score is the time it was sent (ms since start), from this same machine's clock.
      updateLatency.add(Date.now() - data.start - top.score);
    });
    socket.setTimeout(() => socket.close(), holdSeconds * 1000);
  });
  check(res, { 'viewer connected': (r) => r && r.status === 101 });
}

// One submission. Scores always increase, so submissions keep changing the leaderboard.
export function submit(data) {
  const token = data.tokens[exec.scenario.iterationInTest % data.tokens.length];
  const score = Date.now() - data.start;
  const res = http.post(
    `${BASE_URL}/api/v1/contests/${CONTEST_ID}/scores`,
    JSON.stringify({ score }),
    { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` } },
  );
  if (check(res, { 'score accepted': (r) => r.status === 200 })) submissionsOk.add(1);
  submitLatency.add(res.timings.duration);
}

export function handleSummary(data) {
  const value = (metric, key) => (data.metrics[metric] && data.metrics[metric].values[key]) || 0;
  const percentiles = (m) =>
    `p50 ${value(m, 'med').toFixed(0)} ms | p95 ${value(m, 'p(95)').toFixed(0)} ms | p99 ${value(m, 'p(99)').toFixed(0)} ms`;
  const viewers = value('viewers_connected', 'count');
  const ok = value('submissions_ok', 'count');
  const received = value('updates_received', 'count');
  const delivery = ok && viewers ? ((received / (ok * viewers)) * 100).toFixed(1) : '0';

  return {
    stdout: `
=== Rankly load test ===
Settings            : ${VIEWERS} viewers, ${RATE} submissions/s for ${DURATION}s
Viewers connected   : ${viewers} of ${VIEWERS}
Scores accepted     : ${ok} (${(ok / DURATION).toFixed(1)} per second)
Failed HTTP requests: ${(value('http_req_failed', 'rate') * 100).toFixed(2)}%
Skipped submissions : ${value('dropped_iterations', 'count')}
Submit latency      : ${percentiles('submit_latency')}
Updates delivered   : ${received} (${(received / DURATION).toFixed(0)} per second, ${delivery}% of expected)
Update latency      : ${percentiles('update_latency')}
`,
  };
}
