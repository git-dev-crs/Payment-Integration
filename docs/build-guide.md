# 🚀 Build Payment Integration From Scratch — Step-by-Step Guide

> Build a PayPal-clone microservices system using Java 17, Spring Boot 3.5, Kafka, Redis, and Spring Cloud Gateway.

---

## 📋 Prerequisites

Install the following before starting:

| Tool | Version | Download |
|---|---|---|
| Java JDK | 17+ | [adoptium.net](https://adoptium.net) |
| Maven | 3.8+ | [maven.apache.org](https://maven.apache.org) |
| Docker Desktop | Latest | [docker.com](https://docker.com) |
| IntelliJ IDEA | Community/Ultimate | [jetbrains.com](https://jetbrains.com) |
| Postman | Latest | [postman.com](https://postman.com) |

Verify installations:
```bash
java -version      # Should show 17+
mvn -version       # Should show 3.8+
docker --version   # Should show Docker Desktop
```

---

## 🗺️ What We're Building

```
┌─────────────┐     HTTP      ┌─────────────────────────────────────┐
│   Client    │ ──────────►  │  API Gateway (:8080)                │
│  (Postman)  │              │  • JWT validation (global filter)   │
└─────────────┘              │  • Redis rate limiting              │
                             └──────────┬──────────────────────────┘
                                        │ routes to...
               ┌────────────────────────┼──────────────────────────┐
               ▼                        ▼                          ▼
     ┌─────────────────┐    ┌───────────────────────┐   ┌──────────────────┐
     │  User Service   │    │ Transaction Service    │   │Notification Svc  │
     │  (:8081)        │    │ (:8082)               │   │  (:8084)         │
     │  • signup/login │    │ • money transfer      │   │  • Kafka consumer│
     │  • JWT issuance │    │ • 2-phase commit      │   │  • stores alerts │
     └────────┬────────┘    └──────────┬────────────┘   └──────────────────┘
              │ REST                   │ REST + Kafka
              ▼                        ▼
     ┌─────────────────┐    ┌───────────────────────┐   ┌──────────────────┐
     │  Wallet Service │◄───│    Wallet Service      │   │  Reward Service  │
     │  (:8088)        │    │  (hold/capture/credit) │   │  (:8089)         │
     │  • balances     │    └───────────────────────┘   │  • Kafka consumer│
     │  • holds        │                                 │  • reward points │
     └─────────────────┘                                 └──────────────────┘
```

---

## 📁 Phase 1 — Project Structure Setup

### Step 1: Create the Root Maven Project

Create a folder `payment-system/` and inside it create `pom.xml`:

```xml
<!-- payment-system/pom.xml -->
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.paypal</groupId>
    <artifactId>paypal-clone</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>pom</packaging>

    <modules>
        <module>user-service</module>
        <module>transaction-service</module>
        <module>notification-service</module>
        <module>reward-service</module>
        <module>api-gateway</module>
        <module>wallet-service</module>
    </modules>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>3.5.0</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.8.1</version>
                    <configuration>
                        <source>${java.version}</source>
                        <target>${java.version}</target>
                    </configuration>
                </plugin>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <version>3.5.0</version>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

### Step 2: Create docker-compose.yml

```yaml
# payment-system/docker-compose.yml
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.4.1
    ports:
      - "2181:2181"
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000

  kafka:
    image: confluentinc/cp-kafka:7.4.1
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    depends_on:
      - zookeeper

  redis:
    image: redis:7
    ports:
      - "6379:6379"
```

---

## 👤 Phase 2 — User Service

### Step 3: Generate via Spring Initializr

Go to [start.spring.io](https://start.spring.io) and select:
- **Group:** `com.paypal`
- **Artifact:** `user-service`
- **Java:** 17
- **Dependencies:**
  - Spring Web
  - Spring Data JPA
  - H2 Database
  - Spring Security
  - Lombok
  - Spring Cloud (compatibility only)

OR create `user-service/pom.xml` manually:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" ...>
    <parent>
        <groupId>com.paypal</groupId>
        <artifactId>paypal-clone</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>user-service</artifactId>

    <dependencies>
        <dependency><groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
        <dependency><groupId>com.h2database</groupId>
            <artifactId>h2</artifactId><scope>runtime</scope></dependency>
        <dependency><groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId></dependency>
        <dependency><groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId><version>0.11.5</version></dependency>
        <dependency><groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId><version>0.11.5</version></dependency>
        <dependency><groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId><version>0.11.5</version></dependency>
        <dependency><groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId><optional>true</optional></dependency>
    </dependencies>
</project>
```

### Step 4: User Service — Package Structure

```
user-service/src/main/java/com/paypal/user_service/
├── UserServiceApplication.java
├── entity/
│   └── User.java
├── dto/
│   ├── SignupRequest.java
│   ├── LoginRequest.java
│   ├── JwtResponse.java
│   ├── CreateWalletRequest.java
│   └── WalletResponse.java
├── repository/
│   └── UserRepository.java
├── service/
│   ├── UserService.java (interface)
│   └── UserServiceImpl.java
├── controller/
│   ├── AuthController.java
│   └── UserController.java
├── security/
│   └── SecurityConfig.java
├── util/
│   ├── JWTUtil.java
│   └── JWTrequestFilter.java
└── client/
    └── WalletClient.java
```

### Step 5: Key User Service Files to Write

**`User.java` entity:**
```java
@Entity @Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    @Column(unique = true) private String email;
    private String password;  // BCrypt hashed
    private String role;      // e.g., "USER", "ADMIN"
}
```

**`JWTUtil.java`** — generates token with `userId`, `email`, `role` claims:
```java
public String generateToken(User user) {
    return Jwts.builder()
        .setSubject(user.getEmail())
        .claim("userId", user.getId())
        .claim("role", user.getRole())
        .setExpiration(new Date(System.currentTimeMillis() + 86400000))
        .signWith(getSigningKey(), SignatureAlgorithm.HS256)
        .compact();
}
```

**`AuthController.java`** — two endpoints:
```java
POST /auth/signup  → register user, auto-create wallet
POST /auth/login   → return JWT token
```

**`WalletClient.java`** — call wallet-service on signup:
```java
// After saving user, call:
restTemplate.postForEntity("http://localhost:8088/api/v1/wallets",
    new CreateWalletRequest(user.getId(), "INR"), WalletResponse.class);
```

**`application.yml`:**
```yaml
server:
  port: 8081
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
  h2:
    console:
      enabled: true
  jwt:
    secret: my-super-secret-key-that-you-never-share
```

---

## 👛 Phase 3 — Wallet Service

> Build wallet-service BEFORE transaction-service because transaction-service depends on it.

### Step 6: Wallet Service — pom.xml (Dependencies)

```xml
<dependencies>
    <dependency>spring-boot-starter-web</dependency>
    <dependency>spring-boot-starter-data-jpa</dependency>
    <dependency>h2 (runtime)</dependency>
    <dependency>lombok (optional)</dependency>
</dependencies>
```

### Step 7: Wallet Service — Package Structure

```
wallet-service/src/main/java/com/paypal/wallet_service/
├── WalletServiceApplication.java
├── entity/
│   ├── Wallet.java
│   ├── WalletHold.java
│   └── Transaction.java
├── dto/
│   ├── CreateWalletRequest.java
│   ├── CreditRequest.java
│   ├── DebitRequest.java
│   ├── HoldRequest.java
│   ├── HoldResponse.java
│   ├── CaptureRequest.java
│   └── WalletResponse.java
├── repository/
│   ├── WalletRepository.java
│   ├── WalletHoldRepository.java
│   └── TransactionRepository.java
├── service/
│   └── WalletService.java
├── controller/
│   └── WalletController.java
├── exception/
│   ├── InsufficientFundsException.java
│   └── NotFoundException.java
└── scheduler/
    └── HoldExpiryScheduler.java
```

### Step 8: Wallet Entities

**`Wallet.java`:**
```java
@Entity
public class Wallet {
    @Id @GeneratedValue private Long id;
    private Long userId;
    private String currency;
    private Long balance = 0L;
    private Long availableBalance = 0L;
}
```

**`WalletHold.java`:**
```java
@Entity
public class WalletHold {
    @Id @GeneratedValue private Long id;
    @ManyToOne private Wallet wallet;
    private Long amount;
    private String holdReference;
    private String status; // ACTIVE, CAPTURED, RELEASED
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
}
```

### Step 9: Wallet Service Core Logic

The `WalletService` implements these operations:
1. **`createWallet()`** — new Wallet entity, balance=0
2. **`credit()`** — balance + amount, availableBalance + amount
3. **`debit()`** — check sufficient balance, then reduce both
4. **`placeHold()`** — reduce availableBalance only (funds reserved but not gone)
5. **`captureHold()`** — reduce actual balance (finalizes the hold)
6. **`releaseHold()`** — restore availableBalance (cancels the hold)

**`HoldExpiryScheduler.java`:**
```java
@Scheduled(fixedRate = 60000) // every 60 seconds
public void releaseExpiredHolds() {
    List<WalletHold> expired = walletHoldRepository
        .findByStatusAndExpiresAtBefore("ACTIVE", LocalDateTime.now());
    // release each → restore availableBalance
}
```

**`application.yml`:**
```yaml
server:
  port: 8088
spring:
  datasource:
    url: jdbc:h2:mem:walletdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password: password
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
```

---

## 💸 Phase 4 — Transaction Service

### Step 10: Transaction Service pom.xml (Dependencies)

```xml
<dependencies>
    <dependency>spring-boot-starter-web</dependency>
    <dependency>spring-boot-starter-data-jpa</dependency>
    <dependency>h2 (runtime)</dependency>
    <dependency>spring-kafka</dependency>
    <dependency>lombok</dependency>
    <!-- JWT for token parsing -->
    <dependency>jjwt-api 0.11.5</dependency>
    <dependency>jjwt-impl 0.11.5</dependency>
    <dependency>jjwt-jackson 0.11.5</dependency>
</dependencies>
```

### Step 11: Transaction Service — Package Structure

```
transaction-service/src/main/java/com/paypal/transaction_service/
├── TransactionServiceApplication.java
├── entity/
│   └── Transaction.java
├── dto/
│   └── TransferRequest.java
├── repository/
│   └── TransactionRepository.java
├── service/
│   ├── TransactionService.java (interface)
│   └── TransactionServiceImpl.java  ← CORE LOGIC
├── controller/
│   └── TransactionController.java
├── kafka/
│   └── KafkaEventProducer.java
├── client/
│   └── WalletClient.java
├── config/
│   ├── AppConfig.java        (RestTemplate bean)
│   └── JacksonConfig.java    (ObjectMapper bean)
└── util/
    └── JWTUtil.java
```

### Step 12: Transaction Entity

```java
@Entity
public class Transaction {
    @Id @GeneratedValue private Long id;
    private Long senderId;
    private Long receiverId;
    private Double amount;
    private String status;   // PENDING, SUCCESS, FAILED
    private LocalDateTime timestamp;
    private String description;
}
```

### Step 13: KafkaEventProducer

```java
@Component
public class KafkaEventProducer {

    @Autowired
    private KafkaTemplate<String, Transaction> kafkaTemplate;

    public void sendTransactionEvent(String key, Transaction transaction) {
        kafkaTemplate.send("transaction-events", key, transaction);
        System.out.println("🚀 Kafka event sent for transaction: " + key);
    }
}
```

### Step 14: TransactionServiceImpl — The Most Complex Part

The 2-phase commit flow (see existing code):
```
PENDING → hold funds → check receiver → capture → credit → SUCCESS
                 ↓ any failure
              release hold → FAILED (+ compensating refund if capture was done)
```

**`application.yml`:**
```yaml
server:
  port: 8082
spring:
  application:
    name: transaction-service
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    template:
      default-topic: transaction-events
  datasource:
    url: jdbc:h2:mem:transactiondb
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
```

---

## 🔔 Phase 5 — Notification Service

### Step 15: pom.xml (Dependencies)

```xml
<dependencies>
    <dependency>spring-boot-starter-web</dependency>
    <dependency>spring-boot-starter-data-jpa</dependency>
    <dependency>h2 (runtime)</dependency>
    <dependency>spring-kafka</dependency>
    <dependency>lombok</dependency>
    <dependency>jackson-databind</dependency>
</dependencies>
```

### Step 16: Package Structure

```
notification-service/src/main/java/com/paypal/notification_service/
├── NotificationServiceApplication.java
├── entity/
│   ├── Notification.java
│   └── Transaction.java  (mirror DTO for Kafka deserialization)
├── repository/
│   └── NotificationRepository.java
├── service/
│   ├── NotificationService.java
│   └── NotificationServiceImpl.java
├── controller/
│   └── NotificationController.java
├── kafka/
│   └── NotificationConsumer.java
└── config/
    ├── KafkaConsumerConfig.java
    └── JacksonConfig.java
```

### Step 17: NotificationConsumer

```java
@Component
public class NotificationConsumer {

    @Autowired
    private NotificationService notificationService;

    @KafkaListener(topics = "transaction-events", groupId = "notification-group")
    public void consume(Transaction transaction) {
        System.out.println("📬 Notification received for txn: " + transaction.getId());
        notificationService.createNotification(transaction);
    }
}
```

**`application.yml`:**
```yaml
server:
  port: 8084
spring:
  kafka:
    consumer:
      group-id: notification-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.paypal.notification_service.entity"
        spring.json.value.default.type: com.paypal.notification_service.entity.Transaction
```

---

## 🎁 Phase 6 — Reward Service

### Step 18: Package Structure

```
reward-service/src/main/java/com/paypal/reward_service/
├── RewardServiceApplication.java
├── entity/
│   ├── Reward.java
│   └── Transaction.java
├── repository/
│   └── RewardRepository.java
├── service/
│   ├── RewardService.java
│   └── RewardServiceImpl.java
├── controller/
│   └── RewardController.java
├── kafka/
│   └── RewardConsumer.java
└── config/
    ├── KafkaConsumer.java
    └── JacksonConfig.java
```

### Step 19: RewardConsumer

```java
@Component
public class RewardConsumer {

    @Autowired
    private RewardService rewardService;

    @KafkaListener(topics = "transaction-events", groupId = "reward-group")
    public void consume(Transaction transaction) {
        System.out.println("🎁 Reward event for txn: " + transaction.getId());
        // Grant reward points proportional to transaction amount
        rewardService.grantReward(transaction);
    }
}
```

**`application.yml`:**
```yaml
server:
  port: 8089
spring:
  application:
    name: reward-service
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: reward-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
        spring.json.trusted.packages: "*"
        spring.json.value.default.type: com.paypal.reward_service.entity.Transaction
        spring.json.use.type.headers: false
    listener:
      missing-topics-fatal: false
```

---

## 🌐 Phase 7 — API Gateway

### Step 20: pom.xml (Dependencies)

> [!IMPORTANT]
> API Gateway uses **Spring WebFlux** (reactive), NOT Spring MVC. Do NOT add `spring-boot-starter-web` — use `spring-cloud-starter-gateway` instead.

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-gateway</artifactId>
        <version>4.2.0</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId><version>0.11.5</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId><version>0.11.5</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId><version>0.11.5</version>
    </dependency>
</dependencies>
```

### Step 21: Package Structure

```
api-gateway/src/main/java/com/paypal/api_gateway/
├── ApiGatewayApplication.java
├── filters/
│   └── JwtAuthFilter.java  ← GlobalFilter, Ordered
├── util/
│   └── JwtUtil.java
└── config/
    └── RateLimitConfig.java
```

### Step 22: JwtAuthFilter (GlobalFilter)

```java
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final List<String> PUBLIC_PATHS = List.of("/auth/signup", "/auth/login");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Skip auth for public paths
        if (PUBLIC_PATHS.contains(path) || path.startsWith("/auth/")) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        try {
            String token = authHeader.substring(7);
            Claims claims = JwtUtil.validateToken(token);

            // Forward user info as headers to downstream services
            ServerWebExchange mutated = exchange.mutate()
                .request(exchange.getRequest().mutate()
                    .header("X-User-Email", claims.getSubject())
                    .header("X-User-Id", String.valueOf(claims.get("userId")))
                    .header("X-User-Role", (String) claims.get("role"))
                    .build())
                .build();

            return chain.filter(mutated);
        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() { return -100; } // Run before all other filters
}
```

**`application.yml`:**
```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway-service
  redis:
    host: localhost
    port: 6379
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: http://localhost:8081
          predicates:
            - Path=/auth/**

        - id: transaction-service
          uri: http://localhost:8082
          predicates:
            - Path=/api/transactions/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20
                redis-rate-limiter.requestedTokens: 1

        - id: notification-service
          uri: http://localhost:8084
          predicates:
            - Path=/api/notifications/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20

        - id: reward-service
          uri: http://localhost:8085
          predicates:
            - Path=/api/rewards/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20
```

---

## ▶️ Phase 8 — Running the System

### Step 23: Start Infrastructure (Kafka + Redis)

```bash
# In payment-system/ folder
docker-compose up -d

# Verify containers are running
docker ps
```

### Step 24: Start Services (in this order!)

Open 6 separate terminals and run in this order:

```bash
# Terminal 1 — Wallet Service first (others depend on it)
cd wallet-service && mvn spring-boot:run

# Terminal 2 — User Service
cd user-service && mvn spring-boot:run

# Terminal 3 — Transaction Service
cd transaction-service && mvn spring-boot:run

# Terminal 4 — Notification Service
cd notification-service && mvn spring-boot:run

# Terminal 5 — Reward Service
cd reward-service && mvn spring-boot:run

# Terminal 6 — API Gateway (start last)
cd api-gateway && mvn spring-boot:run
```

---

## 🧪 Phase 9 — Testing with Postman

### Test Flow (in this exact order):

**1. Sign Up**
```
POST http://localhost:8080/auth/signup
Content-Type: application/json
{
  "name": "Mohit",
  "email": "mohit@test.com",
  "password": "password123"
}
```

**2. Sign Up second user (receiver)**
```
POST http://localhost:8080/auth/signup
{
  "name": "Receiver",
  "email": "receiver@test.com",
  "password": "password123"
}
```

**3. Login (get JWT)**
```
POST http://localhost:8080/auth/login
{
  "email": "mohit@test.com",
  "password": "password123"
}
→ Save the token from response
```

**4. Add funds to sender wallet**
```
POST http://localhost:8088/api/v1/wallets/credit
{
  "userId": 1,
  "currency": "INR",
  "amount": 10000
}
```

**5. Make a Transaction**
```
POST http://localhost:8080/api/transactions
Authorization: Bearer <your_jwt_token>
{
  "senderId": 1,
  "receiverId": 2,
  "amount": 500.00,
  "description": "Test payment"
}
→ Should return SUCCESS
→ Notification + Reward services should log events
```

**6. Check wallet balances**
```
GET http://localhost:8088/api/v1/wallets/1   ← Sender
GET http://localhost:8088/api/v1/wallets/2   ← Receiver
```

---

## 🐛 Common Issues & Fixes

| Problem | Solution |
|---|---|
| `Kafka connection refused` | Ensure Docker containers are running: `docker-compose up -d` |
| `Wallet not found` after signup | Make sure wallet-service is running when user signs up |
| `401 Unauthorized` on transaction | Add `Authorization: Bearer <token>` header |
| `JWT invalid` in gateway | Ensure the JWT secret matches in user-service and api-gateway |
| `H2 data lost` on restart | Expected — H2 is in-memory. Re-run signup flow after restart |
| `Port already in use` | Kill process: `netstat -ano \| findstr :<PORT>` then `taskkill /PID <pid>` |

---

## 📐 Architecture Patterns Used

| Pattern | Where Used |
|---|---|
| API Gateway | api-gateway — single entry point |
| 2-Phase Commit | TransactionServiceImpl — hold/capture/credit |
| Compensating Transaction | TransactionServiceImpl — refund on failure |
| Event-Driven Architecture | Kafka pub/sub between transaction→notification→reward |
| JWT Stateless Auth | User service issues, gateway validates |
| Rate Limiting | Redis-backed token bucket in gateway |
| Scheduler | HoldExpiryScheduler — auto-release expired holds |

---

## 🔧 Recommended Build Order

```
1. Set up root pom.xml
2. docker-compose.yml (Kafka + Redis)
3. wallet-service  ← Build first, no external deps
4. user-service    ← Calls wallet-service on signup
5. transaction-service ← Calls wallet-service + Kafka
6. notification-service ← Kafka consumer only
7. reward-service  ← Kafka consumer only
8. api-gateway     ← Build last, routes to all others
```
