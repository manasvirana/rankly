# Rankly - Real time Contest Leaderboard Platform

A production-grade real-time leaderboard system built with reactive Spring WebFlux, Redis Sorted Sets, WebSocket fan-out via Redis Pub/Sub, and JWT-based role authentication.


## System Architecture

```
Client (Browser)
      │
      ▼
Spring WebFlux API (Non-blocking, Reactive)
      │
      ├──▶ PostgreSQL via R2DBC
      │    Persistent storage — users, contests, scores
      │    Idempotent upsert — only personal best stored
      │
      ├──▶ Redis Sorted Sets
      │    O(log n) rank updates via ZADD
      │    O(log n) rank lookups via ZREVRANK
      │    Tie-breaking encoded mathematically into score
      │    Per-contest isolation via separate sorted sets
      │
      └──▶ Redis Pub/Sub → WebSocket
           Score update published to Redis channel
           All instances subscribe and push to their clients
           Enables horizontal scaling — N instances, all clients updated
```


## Key Design Decisions

### Why Redis Sorted Sets for rankings?
SQL `ORDER BY` at 100k users = O(n log n) + full table scan on every request. Redis `ZADD` and `ZREVRANK` are O(log n) regardless of dataset size. At scale this is the difference between milliseconds and seconds.

### Why Redis Pub/Sub for WebSocket broadcast?
With in-memory Reactor Sinks, running 2 instances means clients on Instance A never see score updates from Instance B. Redis Pub/Sub makes all instances subscribe to the same channel any score update reaches every connected client on every instance. The API layer becomes stateless and horizontally scalable.

### Why reactive WebFlux + R2DBC?
Traditional Spring MVC uses one thread per request. Under high concurrency (10k users submitting scores simultaneously), this means 10k threads waiting. WebFlux uses a small event loop no thread ever blocks. R2DBC extends this to database queries — fully non-blocking end to end.

### Idempotent score submission
If a client retries a score submission due to a network timeout, the leaderboard doesn't update twice. The database uses an `ON CONFLICT DO UPDATE` upsert only updates if the new score is strictly higher than the existing personal best. Safe to retry from any instance.

### Tie-breaking
Two users submitting the same score: earlier submission wins. Encoded mathematically into the Redis sorted set score as a decimal no distributed locks required, atomically handled by Redis ZADD.


## Tech Stack

| Layer | Technology | Why |
|---|---|---|
| Web Framework | Spring WebFlux | Non-blocking reactive, handles 10k+ concurrent connections |
| Database Driver | R2DBC + PostgreSQL | Fully non-blocking DB queries |
| Rankings | Redis Sorted Sets | O(log n) rank operations at any scale |
| Real-time | WebSocket + Redis Pub/Sub | Live leaderboard push, horizontally scalable |
| Auth | JWT + Spring Security | Stateless authentication, role-based access |
| Password | BCrypt | Industry standard hashing |
| Migrations | Flyway | Version-controlled schema |
| Containerization | Docker | Consistent environments |
| Frontend | React + Vite | Live leaderboard UI |


## API Endpoints

### Auth
```
POST /api/v1/auth/register   — Register new user, returns JWT
POST /api/v1/auth/login      — Login, returns JWT
```

### Contests (ADMIN only for write operations)
```
POST /api/v1/contests              — Create contest [ADMIN]
GET  /api/v1/contests              — List active contests
GET  /api/v1/contests/{id}         — Get contest details
POST /api/v1/contests/{id}/end     — End contest [ADMIN]
```

### Scores & Leaderboard
```
POST /api/v1/contests/{id}/scores           — Submit score [AUTH]
GET  /api/v1/contests/{id}/leaderboard      — Get top N rankings
GET  /api/v1/contests/{id}/rank/{userId}    — Get user's current rank
```

### WebSocket
```
WS /ws/leaderboard/{contestId}   — Subscribe to live leaderboard updates
```


## Role Based Access Control

| Role | Permissions |
|---|---|
| `USER` | Register, login, join contests, submit scores, view leaderboard |
| `ADMIN` | Everything USER can do + create contests, end contests |

JWT tokens carry the user's role. Admin-only endpoints are protected via `@PreAuthorize("hasRole('ADMIN')")`.


## How Horizontal Scaling Works

```
Load Balancer
      │
   ┌──┴──┐
   │     │
 App1   App2      ← Stateless, N instances
   │     │
   └──┬──┘
      │
   Redis          ← Shared state
   ├── Sorted Sets (rankings)
   └── Pub/Sub (WebSocket broadcast)
      │
   PostgreSQL     ← Persistent storage
```

When a score is submitted to App1:
1. Postgres updated via R2DBC
2. Redis Sorted Set updated
3. Update published to Redis channel `rankly:contest:{id}`
4. App1 AND App2 both receive it (both subscribed)
5. All WebSocket clients on both instances get the update


## Local Setup

### Prerequisites
- Java 21
- Docker Desktop
- Maven

### Run

```bash
# Clone the repo
git clone https://github.com/manasvirana/rankly.git
cd rankly

# Start Postgres and Redis
docker compose up -d

# Create tables (first time only)
docker exec -it rankly-postgres-1 psql -U admin -d leaderboard

# Run the Spring Boot app
mvn spring-boot:run

# Run the frontend
cd frontend
npm install
npm run dev
```

App runs at `http://localhost:5173`  
API runs at `http://localhost:8080`


## Project Structure

```
rankly/
├── src/main/java/com/leaderboard/
│   ├── config/
│   │   ├── RedisConfig.java          — Reactive Redis setup
│   │   ├── R2dbcConfig.java          — R2DBC connection config
│   │   ├── SecurityConfig.java       — JWT security rules
│   │   ├── JwtAuthFilter.java        — JWT request filter
│   │   ├── CorsConfig.java           — CORS configuration
│   │   └── GlobalErrorHandler.java   — Global exception handling
│   ├── controller/
│   │   ├── AuthController.java       — Register, login
│   │   ├── ContestController.java    — Contest CRUD
│   │   ├── ScoreController.java      — Score submission, leaderboard
│   │   └── HealthController.java     — Health check
│   ├── service/
│   │   ├── RedisLeaderboardService.java     — Core ranking engine
│   │   ├── LeaderboardBroadcastService.java — Redis Pub/Sub fan-out
│   │   ├── ScoreService.java                — Score orchestration
│   │   ├── AuthService.java                 — Auth logic
│   │   └── JwtService.java                  — JWT operations
│   ├── repository/
│   │   ├── UserRepository.java
│   │   ├── ContestRepository.java
│   │   └── ScoreRepository.java      — Idempotent upsert
│   ├── model/
│   │   ├── User.java
│   │   ├── Contest.java
│   │   └── Score.java
│   ├── dto/
│   │   ├── Requests.java
│   │   ├── LeaderboardEntry.java
│   │   ├── LeaderboardUpdate.java
│   │   ├── AuthRequests.java
│   │   └── AuthResponse.java
│   └── websocket/
│       └── LeaderboardWebSocketHandler.java
├── frontend/                         — React + Vite frontend
├── docker-compose.yml                — Local Postgres + Redis
└── Dockerfile                        — Production build
```

## Screenshots 
Live LeaderBoard
<img width="1861" height="955" alt="Screenshot 2026-03-19 235008" src="https://github.com/user-attachments/assets/e30e5499-612b-4246-8aa7-012c20ee8ebb" />
Homepage
<img width="1920" height="974" alt="Screenshot 2026-03-19 233540" src="https://github.com/user-attachments/assets/228c1b90-2b43-4b5c-95c2-4004add189da" />
Admin Panel
<img width="1920" height="976" alt="Screenshot 2026-03-19 233824" src="https://github.com/user-attachments/assets/a15cb795-7d9b-4928-ae9c-6948ef763390" />

## What I Learned Building This

The most interesting engineering problem was the **tie-breaking in Redis Sorted Sets**. Redis sorted sets rank by a single `double` score. When two users have the same score, I needed earlier submissions to rank higher without distributed locks.

The solution: encode score as `rawScore + (1.0 / (epochMs % 1_000_000))`. The raw score dominates (integer part), and the fractional part encodes submission time earlier submissions produce a slightly larger decimal, ranking them higher. Atomic, lock-free, and handled entirely by Redis.


Built by [Manasvi Singh](https://github.com/manasvirana)
