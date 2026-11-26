# Load test results

Run on 26 November 2026 to measure one change: sharing a single Redis Pub/Sub subscription per
contest on each app instance, instead of one per WebSocket viewer.

| | Commit |
|---|---|
| Before | `f66925e` |
| After | `9feb5a7` |

## Setup

| Part | Details |
|---|---|
| Application server | AWS EC2 `t4g.small` (2 vCPU, 2 GB), Mumbai (`ap-south-1`) |
| Running on it | Caddy, 2 app instances (512 MiB limit, 256 MiB heap each), PostgreSQL 15, Redis 7 |
| Load generator | Separate EC2 `c7i-flex.large` (2 vCPU, 4 GB), same region |
| Network path | HTTPS and WSS through Caddy, over the VPC private network |
| Tool | k6 (`grafana/k6` Docker image, v2), script: [`rankly.js`](rankly.js) |
| Load | 100 or 500 viewers on one contest, 5 score submissions/s for 60 s, 20 submitting players |
| Ramp | Viewers connect over 30 s |
| Runs | 3 per level, each on a new empty contest. Tables show the median of the 3 runs |

**Update latency** is the time from sending a score to a viewer receiving the leaderboard that
contains it. Each score is the number of milliseconds since the test started, so both times come
from the load generator's clock.

## Summary

| Median of 3 runs | 100 before | 100 after | 500 before | 500 after |
|---|---|---|---|---|
| Peak Redis connections | 105 | 7 | 505 | 7 |
| Update latency p50 | 21 ms | 21 ms | 40 ms | 30 ms |
| Update latency p95 | 38 ms | 34 ms | 71 ms | 54 ms |
| Update latency p99 | 68 ms | 57 ms | 102 ms | 88 ms |
| Submit latency p95 | 39 ms | 33 ms | 82 ms | 65 ms |
| Messages delivered per second | ~500 | ~500 | ~2,500 | ~2,500 |
| Delivered (of expected) | 100% | 100% | 100% | 100% |
| Failed HTTP requests | 0% | 0% | 0% | 0% |

## Findings

- **Redis connections no longer grow with viewers.** Before, there was one per viewer plus 5.
  After, the peak was 7 at both 100 and 500 viewers.
- **At 500 viewers, latency fell** by 25% (p50), 24% (p95) and 14% (p99). Every run after the
  change was faster than every run before it at all three percentiles.
- **At 100 viewers, latency was about the same.** The differences are within run-to-run variation.
- The likely reason for the improvement is that Redis now sends each update twice (once per
  instance) instead of once per viewer, and each instance parses it once. This was not profiled.
- **CPU showed no consistent change** (see below).

## All runs

Latencies are `p50 / p95 / p99` in milliseconds.

| Viewers | Code | Run | Scores accepted | Update latency | Submit latency |
|---|---|---|---|---|---|
| 100 | before | 1 | 301 | 23 / 41 / 63 | 26 / 45 / 72 |
| 100 | before | 2 | 300 | 21 / 38 / 70 | 24 / 39 / 54 |
| 100 | before | 3 | 301 | 20 / 32 / 68 | 23 / 35 / 70 |
| 100 | after | 1 | 301 | 24 / 40 / 69 | 25 / 42 / 70 |
| 100 | after | 2 | 301 | 21 / 34 / 57 | 21 / 33 / 53 |
| 100 | after | 3 | 301 | 18 / 29 / 45 | 19 / 30 / 52 |
| 500 | before | 1 | 301 | 41 / 71 / 102 | 57 / 79 / 95 |
| 500 | before | 2 | 300 | 40 / 70 / 100 | 56 / 82 / 111 |
| 500 | before | 3 | 301 | 40 / 71 / 144 | 57 / 84 / 109 |
| 500 | after | 1 | 301 | 30 / 54 / 94 | 35 / 62 / 84 |
| 500 | after | 2 | 301 | 30 / 60 / 88 | 31 / 65 / 90 |
| 500 | after | 3 | 301 | 29 / 51 / 80 | 35 / 65 / 87 |

## Server peaks

Sampled every few seconds with `redis-cli info clients` and `docker stats`.

| | 100 before | 100 after | 500 before | 500 after |
|---|---|---|---|---|
| Redis connections | 105 | 7 | 505 | 7 |
| Redis memory (MiB) | 6.7 | 7.7 | 14.0 | 7.7 |
| Redis CPU | 4.6% | 6.1% | 9.7% | 6.3% |
| App 1 CPU / memory (MiB) | 49% / 391 | 34% / 385 | 37% / 421 | 46% / 399 |
| App 2 CPU / memory (MiB) | 53% / 387 | 34% / 376 | 35% / 418 | 56% / 390 |

## Limitations

- Short runs (60 s), three per level, at one submission rate. This is not a capacity or soak test,
  and the system's breaking point was not measured.
- Both app instances share one server with PostgreSQL and Redis. This shows updates fanning out
  across instances, not high availability or horizontal capacity.
- The "after" runs followed a redeploy, with a short partial warm-up run first. The "before" runs
  were on instances that had been running longer.
- Peak sampling can miss short spikes. The 500-viewer "before" peaks come from one recorded run.
- When submissions arrive very close together, an update can already contain a newer score, so
  latency can be slightly underestimated.

## Reproduce

Create an empty contest for each run (on the application server):

```bash
docker compose exec -T postgres psql -U admin -d leaderboard -qtA -c \
  "insert into contests(name,starts_at,ends_at) values ('Load test', now(), now() + interval '7 days') returning id"
```

Run the test (on the load generator):

```bash
docker run --rm --ulimit nofile=65535:65535 -v "$PWD/loadtest:/scripts" \
  -e BASE_URL=https://your-domain -e CONTEST_ID=<contest-id> \
  -e VIEWERS=500 -e RATE=5 -e DURATION=60 -e RAMP=30 \
  grafana/k6 run --quiet /scripts/rankly.js
```

The script registers test accounts on the target, so point it only at a system you own.
