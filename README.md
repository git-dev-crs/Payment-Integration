# Payment Integration — Spring Boot Microservices

A **PayPal-clone** payment system built with Java 17, Spring Boot 3.5, Apache Kafka, Redis, and Spring Cloud Gateway.

## 🏗️ Architecture

```
Client → API Gateway (:8080) → [User / Transaction / Notification / Reward Services]
                                      ↓ REST
                                 Wallet Service (:8088)
                                      ↓ Kafka
                          [Notification (:8084) + Reward (:8089)]
```

## 📦 Services

| Service | Port | Role |
|---|---|---|
| api-gateway | 8080 | JWT auth, rate limiting (Redis) |
| user-service | 8081 | Signup, login, JWT issuance |
| transaction-service | 8082 | Money transfers (2-phase commit) |
| wallet-service | 8088 | Balance, holds, credit/debit |
| notification-service | 8084 | Kafka consumer — alerts |
| reward-service | 8089 | Kafka consumer — reward points |

## 🚀 Tech Stack

- **Java 17** + **Spring Boot 3.5**
- **Spring Cloud Gateway** (reactive/WebFlux)
- **Apache Kafka** — async event bus
- **Redis** — rate limiting
- **H2** — in-memory database (dev)
- **JWT (jjwt 0.11.5)** — stateless auth
- **Maven** multi-module build

## ▶️ Running Locally

### 1. Start Infrastructure
```bash
docker-compose up -d   # starts Kafka + Zookeeper + Redis
```

### 2. Start Services (in order)
```bash
# Each in a separate terminal
cd wallet-service       && ./mvnw spring-boot:run
cd user-service         && ./mvnw spring-boot:run
cd transaction-service  && ./mvnw spring-boot:run
cd notification-service && ./mvnw spring-boot:run
cd reward-service       && ./mvnw spring-boot:run
cd api-gateway          && ./mvnw spring-boot:run
```

### 3. Test Flow (Postman)
1. `POST /auth/signup` — register user (auto-creates wallet)
2. `POST /auth/login` — get JWT token
3. `POST /api/v1/wallets/credit` — add funds
4. `POST /api/transactions` — transfer money (with Bearer token)
5. Check Notification + Reward services log the Kafka event

## 🔑 Key Design Patterns

- **2-Phase Commit** — Hold → Capture → Credit with compensating rollback
- **Event-Driven** — Kafka `transaction-events` topic → Notification + Reward
- **JWT Gateway Filter** — validates token, injects `X-User-Id` header downstream
- **Redis Rate Limiting** — 10 req/s per route on gateway
- **Hold Expiry Scheduler** — auto-releases stale wallet holds every 60s
