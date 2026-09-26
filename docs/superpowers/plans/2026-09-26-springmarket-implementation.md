# SpringMarket Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build SpringMarket — a cohesive marketplace modular monolith (Spring Boot + React) that teaches Data JPA, Mongo, Redis, Security, Kafka, RabbitMQ, Batch, Mail, Actuator, and OpenAPI through one domain.

**Architecture:** Single Spring Boot 3.4 app with package-level modules (`identity`, `catalog`, `cart`, `order`, `payment`, `inventory`, `notification`, `reporting`, `shared`), Docker Compose for infra, React+Vite+TS SPA for thin UI. Postgres is system of record; Mongo holds products/reviews; Redis caches listings; Kafka carries domain events; RabbitMQ carries notification commands.

**Tech Stack:** Java 21, Spring Boot 3.4.x, Maven, Spring Security (JWT), Spring Data JPA, Flyway, Spring Data MongoDB, Spring Data Redis, Spring for Apache Kafka, Spring AMQP, Spring Batch, Spring Mail, springdoc-openapi, Testcontainers, React 19, Vite, TypeScript.

**Spec:** `docs/superpowers/specs/2026-09-26-springmarket-design.md`

## Global Constraints

- Java 21 LTS; Spring Boot 3.4.x; Maven build under `backend/`
- Base API path `/api/v1/...`; Problem Details for errors; springdoc OpenAPI
- Roles: `BUYER`, `SELLER`, `ADMIN`; JWT access + hashed refresh tokens in Postgres
- Cart storage: Postgres/JPA only (no Redis cart in v1)
- Payments: simulated only; idempotency keys required
- Brokers: Kafka = domain events; RabbitMQ = notification commands (+ DLQ)
- Secrets via `.env` / Compose; never commit real secrets
- Package root: `com.springmarket`
- SPA stays thin — functional, not a polished storefront
- No Spring Cloud microservices in this plan

## Review Focus

These failure modes are easy to miss; each is pinned by a test in the owning task:

1. **Concurrent checkout / insufficient stock** — two buyers cannot both reserve the last unit; expect `409` / `InsufficientStock` for the loser → Task 6
2. **Payment double-submit** — same idempotency key returns the original payment result, not a second charge → Task 7
3. **Illegal order transition** — e.g. `DELIVERED` → `PENDING` rejected with Problem Details → Task 6
4. **Kafka redelivery** — duplicate `PaymentCompleted` with same `eventId` does not flip status twice or double-side-effect → Task 8
5. **Revoked/expired refresh token** — reuse after logout/rotate returns `401` → Task 2

---

## File structure (target)

```
SpringMegaProject/
├── .env.example
├── docker-compose.yml
├── README.md
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/springmarket/
│       │   │   ├── SpringMarketApplication.java
│       │   │   ├── shared/
│       │   │   │   ├── error/ApiExceptionHandler.java
│       │   │   │   ├── error/DomainException.java
│       │   │   │   ├── error/ProblemDetailFactory.java
│       │   │   │   ├── money/Money.java
│       │   │   │   └── security/… (JwtService, SecurityConfig, filters)
│       │   │   ├── identity/
│       │   │   ├── catalog/
│       │   │   ├── cart/
│       │   │   ├── order/
│       │   │   ├── payment/
│       │   │   ├── inventory/
│       │   │   ├── notification/
│       │   │   └── reporting/
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-local.yml
│       │       └── db/migration/V1__….sql …
│       └── test/java/com/springmarket/…
└── frontend/
    ├── package.json
    ├── vite.config.ts
    └── src/…
```

---

### Task 1: Backend scaffold + Docker Compose (Postgres first)

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/springmarket/SpringMarketApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/application-local.yml`
- Create: `docker-compose.yml` (postgres service only in this task; other services added when needed)
- Create: `.env.example`
- Create: `backend/src/test/java/com/springmarket/SpringMarketApplicationTests.java`
- Modify: root `.gitignore` (already exists — ensure backend/frontend ignores present)

**Interfaces:**
- Consumes: none
- Produces: runnable Boot app on `:8080` with profile `local`; Compose Postgres on `:5432` database `springmarket`

- [ ] **Step 1: Write the failing context-load test**

```java
package com.springmarket;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SpringMarketApplicationTests {
    @Test
    void contextLoads() {}
}
```

Also add `backend/src/test/resources/application-test.yml` with H2 or Testcontainers later — for Task 1 use:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
  flyway:
    enabled: false
```

Add H2 test dependency in `pom.xml` for this task only (Flyway enabled in later tasks with Postgres Testcontainers).

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=SpringMarketApplicationTests`  
Expected: FAIL (no project / no wrapper yet)

- [ ] **Step 3: Create Maven project**

`pom.xml` parent `spring-boot-starter-parent` `3.4.5`, Java 21, dependencies for this task:

- `spring-boot-starter-web`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-validation`
- `flyway-core` + `flyway-database-postgresql`
- `postgresql`
- `spring-boot-starter-actuator`
- `springdoc-openapi-starter-webmvc-ui` `2.8.5` (or current compatible)
- `spring-boot-starter-test`
- `h2` (test scope)

Main class:

```java
package com.springmarket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SpringMarketApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpringMarketApplication.class, args);
    }
}
```

`application.yml`:

```yaml
spring:
  application:
    name: springmarket
  profiles:
    default: local

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

`application-local.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/springmarket
    username: springmarket
    password: springmarket
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        jdbc:
          time_zone: UTC
  flyway:
    enabled: true

springdoc:
  api-docs:
    path: /api/v1/api-docs
  swagger-ui:
    path: /swagger-ui.html
```

**Task 1 Flyway:** keep `spring.flyway.enabled: false` and `jpa.hibernate.ddl-auto: none` in `application-local.yml`. Task 2 enables Flyway and adds `V1__identity.sql`.

`docker-compose.yml`:

```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: springmarket
      POSTGRES_USER: springmarket
      POSTGRES_PASSWORD: springmarket
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U springmarket"]
      interval: 5s
      timeout: 5s
      retries: 10

volumes:
  pgdata:
```

`.env.example`:

```bash
POSTGRES_DB=springmarket
POSTGRES_USER=springmarket
POSTGRES_PASSWORD=springmarket
JWT_SECRET=change-me-to-a-long-random-string-at-least-32-chars
MAIL_HOST=localhost
MAIL_PORT=1025
```

Generate Maven wrapper: `cd backend && mvn -N wrapper:wrapper -Dmaven=3.9.9`

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=SpringMarketApplicationTests`  
Expected: PASS

- [ ] **Step 5: Smoke-run against Compose**

```bash
docker compose up -d postgres
cd backend && ./mvnw spring-boot:run
curl -s http://localhost:8080/actuator/health
```

Expected: `{"status":"UP"}` (or UP with components)

- [ ] **Step 6: Commit**

```bash
git add backend docker-compose.yml .env.example .gitignore
git commit -m "chore: scaffold Spring Boot backend and Postgres Compose"
```

---

### Task 2: Identity — users, register/login, JWT + refresh

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__identity.sql`
- Create: `backend/src/main/java/com/springmarket/identity/User.java`
- Create: `backend/src/main/java/com/springmarket/identity/Role.java`
- Create: `backend/src/main/java/com/springmarket/identity/UserRepository.java`
- Create: `backend/src/main/java/com/springmarket/identity/RefreshToken.java`
- Create: `backend/src/main/java/com/springmarket/identity/RefreshTokenRepository.java`
- Create: `backend/src/main/java/com/springmarket/identity/AuthService.java`
- Create: `backend/src/main/java/com/springmarket/identity/AuthController.java`
- Create: `backend/src/main/java/com/springmarket/identity/dto/RegisterRequest.java`
- Create: `backend/src/main/java/com/springmarket/identity/dto/LoginRequest.java`
- Create: `backend/src/main/java/com/springmarket/identity/dto/AuthResponse.java`
- Create: `backend/src/main/java/com/springmarket/identity/dto/RefreshRequest.java`
- Create: `backend/src/main/java/com/springmarket/shared/security/JwtService.java`
- Create: `backend/src/main/java/com/springmarket/shared/security/JwtAuthFilter.java`
- Create: `backend/src/main/java/com/springmarket/shared/security/SecurityConfig.java`
- Create: `backend/src/main/java/com/springmarket/shared/security/UserPrincipal.java`
- Create: `backend/src/main/java/com/springmarket/shared/error/DomainException.java`
- Create: `backend/src/main/java/com/springmarket/shared/error/ApiExceptionHandler.java`
- Create: `backend/src/test/java/com/springmarket/identity/AuthServiceTest.java`
- Create: `backend/src/test/java/com/springmarket/identity/AuthControllerIT.java`
- Modify: `backend/pom.xml` — add `spring-boot-starter-security`, `jjwt-api/impl/jackson` `0.12.6`
- Modify: `application-local.yml` — enable Flyway, JWT secret, CORS origin `http://localhost:5173`

**Interfaces:**
- Consumes: Boot app from Task 1
- Produces:
  - `AuthService.register(RegisterRequest): AuthResponse`
  - `AuthService.login(LoginRequest): AuthResponse`
  - `AuthService.refresh(String refreshToken): AuthResponse`
  - `AuthService.logout(String refreshToken): void`
  - `AuthResponse(String accessToken, String refreshToken, String tokenType, long expiresInSeconds, UUID userId, Set<String> roles)`
  - `JwtService.generateAccessToken(UserPrincipal): String`
  - `JwtService.parse(String): UserPrincipal`
  - REST: `POST /api/v1/auth/register|login|refresh|logout`

- [ ] **Step 1: Write failing unit test for register + refresh revoke**

```java
@Test
void refreshAfterLogout_fails() {
    AuthResponse first = authService.register(new RegisterRequest(
        "buyer@example.com", "Password1!", Set.of("BUYER")));
    authService.logout(first.refreshToken());
    assertThrows(DomainException.class, () -> authService.refresh(first.refreshToken()));
}
```

Use `@ExtendWith(MockitoExtension.class)` or `@DataJpaTest` + security beans — prefer service test with mocked repos for pure unit, plus one `@SpringBootTest` + MockMvc IT for HTTP.

Also pin Review Focus #5:

```java
@Test
void expiredRefreshToken_rejected() { /* token expires_at in past → DomainException UNAUTHORIZED */ }

@Test
void rotatedRefreshToken_oldTokenRejected() {
    AuthResponse first = authService.login(...);
    AuthResponse second = authService.refresh(first.refreshToken());
    assertThrows(DomainException.class, () -> authService.refresh(first.refreshToken()));
}
```

- [ ] **Step 2: Run tests — expect FAIL**

Run: `cd backend && ./mvnw test -Dtest=AuthServiceTest`  
Expected: FAIL (classes missing)

- [ ] **Step 3: Flyway V1 + entities + security**

`V1__identity.sql`:

```sql
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
```

Enable Flyway in `application-local.yml` (`ddl-auto: validate`).

`Role` enum: `BUYER`, `SELLER`, `ADMIN`.

`JwtService` uses HMAC secret from `app.jwt.secret` (min 256 bits), access TTL 15m, refresh TTL 7d.

`SecurityConfig`:

- CSRF off (stateless JWT)
- `authorizeHttpRequests`: permit `/api/v1/auth/**`, `/actuator/health`, `/swagger-ui/**`, `/v3/api-docs/**`, `/api/v1/api-docs/**`; authenticate everything else
- Stateless session
- Add `JwtAuthFilter` before `UsernamePasswordAuthenticationFilter`
- CORS for `http://localhost:5173`

`ApiExceptionHandler` maps `DomainException` → ProblemDetail with status from exception.

Password: `PasswordEncoder` BCrypt.

Default register role: if roles empty, assign `BUYER`. Reject unknown roles. Only `ADMIN` bootstrap later (seed in Task 14 or SQL `V2__seed_admin.sql` optional).

- [ ] **Step 4: Implement AuthController + pass tests**

```java
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }
    // login, refresh, logout similarly
}
```

`RegisterRequest` / `LoginRequest`: `@Email`, `@NotBlank`, password `@Size(min=8)`.

- [ ] **Step 5: Run tests**

Run: `cd backend && ./mvnw test -Dtest=AuthServiceTest,AuthControllerIT`  
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend
git commit -m "feat: identity module with JWT register/login/refresh"
```

---

### Task 3: Shared errors + OpenAPI hardening

**Files:**
- Create/Modify: `shared/error/*`
- Create: `backend/src/main/java/com/springmarket/shared/config/OpenApiConfig.java`
- Create: `backend/src/test/java/com/springmarket/shared/error/ApiExceptionHandlerTest.java`

**Interfaces:**
- Consumes: `DomainException`
- Produces: consistent Problem Details; OpenAPI bearer security scheme named `bearer-jwt`

- [ ] **Step 1: Failing test — validation error shape**

```java
@mockMvc
perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON).content("{}"))
.andExpect(status().isBadRequest())
.andExpect(jsonPath("$.type").exists())
.andExpect(jsonPath("$.detail").exists());
```

- [ ] **Step 2: Run — expect FAIL or incomplete body**
- [ ] **Step 3: Implement handler for `MethodArgumentNotValidException` + `OpenApiConfig`**
- [ ] **Step 4: Tests PASS**
- [ ] **Step 5: Commit** `feat: problem-details errors and OpenAPI JWT scheme`

---

### Task 4: Catalog — Mongo products + Redis list cache

**Files:**
- Modify: `docker-compose.yml` — add `mongo`, `redis`
- Modify: `pom.xml` — `spring-boot-starter-data-mongodb`, `spring-boot-starter-data-redis`
- Create: `catalog/ProductDocument.java`, `ProductRepository.java`, `ReviewDocument.java`, `ReviewRepository.java`
- Create: `catalog/CatalogService.java`, `CatalogController.java`, DTOs
- Create: `catalog/ProductCacheService.java`
- Create: `inventory` stub deferred — product create also initializes inventory in Task 5/6
- Create: tests `CatalogServiceTest`, `CatalogControllerIT`
- Modify: `SecurityConfig` — `GET /api/v1/products/**` public; `POST/PUT/DELETE` require `SELLER`

**Interfaces:**
- Consumes: authenticated `UserPrincipal` for seller id
- Produces:
  - `CatalogService.create(sellerId, CreateProductRequest): ProductResponse`
  - `CatalogService.list(page, size): Page<ProductResponse>` (Redis cache key `catalog:products:page:{n}:size:{s}`)
  - `CatalogService.get(productId): ProductResponse`
  - `CatalogService.addReview(productId, buyerId, ReviewRequest): ReviewResponse`
  - REST: `/api/v1/products`, `/api/v1/products/{id}`, `/api/v1/products/{id}/reviews`

- [ ] **Step 1: Failing test — list caches after first load**

```java
@Test
void list_usesRedisOnSecondCall() {
    catalogService.create(...);
    catalogService.list(0, 20);
    verify(productRepository, times(1)).findAll(any(Pageable.class)); // or mongo equivalent
    catalogService.list(0, 20);
    verify(productRepository, times(1)).findAll(any(Pageable.class)); // still 1 — served from cache
}
```

(If using Spring Cache `@Cacheable("productList")`, verify via mock Redis or spy repository.)

- [ ] **Step 2: Run — FAIL**
- [ ] **Step 3: Implement Mongo documents + Redis cache + controller**

`ProductDocument` fields: `id` (String/UUID), `sellerId`, `name`, `description`, `priceAmount` (BigDecimal), `currency` (`USD`), `category`, `active`, `createdAt`.

On create/update/delete: `cacheManager.getCache("productList").clear()`.

Compose:

```yaml
  mongo:
    image: mongo:7
    ports: ["27017:27017"]
    volumes: [mongodata:/data/db]
  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]
```

`application-local.yml` mongo URI `mongodb://localhost:27017/springmarket`, redis `localhost:6379`.

- [ ] **Step 4: Tests PASS**
- [ ] **Step 5: Commit** `feat: catalog with Mongo products and Redis cache`

---

### Task 5: Cart (JPA)

**Files:**
- Create: `V2__cart.sql`
- Create: `cart/Cart.java`, `CartItem.java`, `CartRepository.java`, `CartService.java`, `CartController.java`, DTOs
- Create: `CartServiceTest.java`
- Modify: `SecurityConfig` — cart endpoints authenticated

**Interfaces:**
- Consumes: `CatalogService.get(productId)` for price snapshot
- Produces:
  - `CartService.getCart(userId): CartResponse`
  - `CartService.addItem(userId, productId, qty): CartResponse`
  - `CartService.updateItem(...)`, `removeItem(...)`, `clear(userId)`
  - REST: `/api/v1/cart`

`V2__cart.sql`:

```sql
CREATE TABLE carts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id),
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE cart_items (
    id UUID PRIMARY KEY,
    cart_id UUID NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id VARCHAR(36) NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    UNIQUE (cart_id, product_id)
);
```

- [ ] **Step 1: Failing test — add item snapshots price**

```java
@Test
void addItem_snapshotsUnitPrice() {
    when(catalogService.get(productId)).thenReturn(productWithPrice("19.99"));
    CartResponse cart = cartService.addItem(userId, productId, 2);
    assertEquals(new BigDecimal("19.99"), cart.items().getFirst().unitPrice());
    assertEquals(2, cart.items().getFirst().quantity());
}
```

- [ ] **Step 2: FAIL → implement → PASS**
- [ ] **Step 3: Commit** `feat: JPA shopping cart with price snapshots`

---

### Task 6: Order + inventory (transactional checkout)

**Files:**
- Create: `V3__orders_inventory.sql`
- Create: `inventory/InventoryItem.java`, `InventoryRepository.java`, `InventoryService.java`
- Create: `order/Order.java`, `OrderItem.java`, `OrderStatus.java`, `OrderRepository.java`, `OrderService.java`, `OrderController.java`
- Create: `order/OrderStatusMachine.java`
- Create: tests including stock race and illegal transition
- Hook: on product create (Task 4), call `inventoryService.initialize(productId, initialQty)` — add `initialStock` to `CreateProductRequest`

**Interfaces:**
- Consumes: `CartService`, `InventoryService`, (Task 7) `PaymentService.charge`
- Produces:
  - `InventoryService.initialize(productId, qty)`
  - `InventoryService.reserve(productId, qty)` / `release(...)`
  - `OrderService.placeOrder(buyerId): OrderResponse` — PENDING, reserve stock, clear cart
  - `OrderService.applyPaymentResult(orderId, paid: boolean)`
  - `OrderStatusMachine.assertCanTransition(from, to)`
  - REST: `POST /api/v1/orders`, `GET /api/v1/orders/mine`, `GET /api/v1/orders/{id}`

`V3__orders_inventory.sql`:

```sql
CREATE TABLE inventory_items (
    product_id VARCHAR(36) PRIMARY KEY,
    available_qty INT NOT NULL CHECK (available_qty >= 0),
    reserved_qty INT NOT NULL CHECK (reserved_qty >= 0),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    buyer_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(32) NOT NULL,
    total_amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id VARCHAR(36) NOT NULL,
    quantity INT NOT NULL,
    unit_price NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL
);
```

Use `@Version` on `InventoryItem` for optimistic locking (Review Focus #1).

- [ ] **Step 1: Failing tests**

```java
@Test
void placeOrder_whenInsufficientStock_throws() {
    inventoryService.initialize(productId, 1);
    // cart qty 2
    assertThrows(InsufficientStockException.class, () -> orderService.placeOrder(buyerId));
}

@Test
void concurrentReserve_onlyOneSucceeds() {
    // two threads reserve last unit; one InsufficientStockException
}

@Test
void illegalTransition_deliveredToPending_rejected() {
    assertThrows(InvalidOrderStateException.class,
        () -> orderStatusMachine.assertCanTransition(OrderStatus.DELIVERED, OrderStatus.PENDING));
}
```

- [ ] **Step 2: FAIL → implement status machine + transactional placeOrder → PASS**

`placeOrder` `@Transactional`:
1. Load cart; reject empty
2. For each line `inventoryService.reserve`
3. Persist order PENDING + items
4. `cartService.clear`
5. Return response (payment + events in Tasks 7–8)

- [ ] **Step 3: Commit** `feat: orders and inventory with transactional checkout`

---

### Task 7: Simulated payment + idempotency

**Files:**
- Create: `V4__payments.sql`
- Create: `payment/Payment.java`, `PaymentRepository.java`, `PaymentService.java`
- Modify: `OrderService.placeOrder` to call `paymentService.charge(orderId, amount, currency, idempotencyKey)`
- Create: `PaymentServiceTest.java`

**Interfaces:**
- Consumes: order id + money
- Produces:
  - `PaymentService.charge(UUID orderId, BigDecimal amount, String currency, String idempotencyKey): PaymentResult`
  - `PaymentResult(UUID paymentId, PaymentStatus status, String idempotencyKey)`
  - On SUCCESS → `orderService.applyPaymentResult(orderId, true)`; on FAILURE → false + release inventory

`V4__payments.sql`:

```sql
CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    status VARCHAR(32) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL
);
```

Simulation rule: fail if `idempotencyKey` ends with `-FAIL`; else succeed (learning hook).

- [ ] **Step 1: Failing idempotency test (Review Focus #2)**

```java
@Test
void charge_sameIdempotencyKey_returnsOriginalAndDoesNotDoubleInsert() {
    PaymentResult a = paymentService.charge(orderId, amount, "USD", "key-1");
    PaymentResult b = paymentService.charge(orderId, amount, "USD", "key-1");
    assertEquals(a.paymentId(), b.paymentId());
    assertEquals(1, paymentRepository.count());
}
```

- [ ] **Step 2: Implement → PASS**
- [ ] **Step 3: Wire into placeOrder** (idempotency key = `orderId` string for sync path)
- [ ] **Step 4: Commit** `feat: simulated payments with idempotency keys`

---

### Task 8: Kafka domain events

**Files:**
- Modify: `docker-compose.yml` — Kafka (KRaft single node) or Redpanda for simplicity
- Modify: `pom.xml` — `spring-kafka`, `spring-kafka-test`
- Create: `shared/events/OrderPlacedEvent.java`, `PaymentCompletedEvent.java`, `PaymentFailedEvent.java`
- Create: `order/OrderEventPublisher.java`, `payment/PaymentEventPublisher.java`
- Create: `order/PaymentResultListener.java` (consumes payment events if payment becomes async; for sync path still publish events after charge)
- Create: `shared/events/ProcessedEvent.java` + `V5__processed_events.sql` for idempotent consumers
- Create: `PaymentResultListenerTest` with `@EmbeddedKafka` or Testcontainers

**Interfaces:**
- Topics: `order.placed`, `payment.completed`, `payment.failed`
- Event envelope: `eventId` (UUID), `occurredAt`, payload fields
- `ProcessedEventRepository.existsByEventId` — skip if present (Review Focus #4)

Recommend **Redpanda** in Compose for lighter local Kafka API:

```yaml
  redpanda:
    image: redpandadata/redpanda:v24.2.4
    command:
      - redpanda start --overprovisioned --smp 1 --memory 512M
        --reserve-memory 0M --node-id 0 --check=false
        --kafka-addr PLAINTEXT://0.0.0.0:9092
        --advertise-kafka-addr PLAINTEXT://localhost:9092
    ports: ["9092:9092"]
```

- [ ] **Step 1: Failing test — duplicate eventId ignored**

```java
@Test
void duplicatePaymentCompleted_doesNotReapply() {
    listener.onPaymentCompleted(event);
    listener.onPaymentCompleted(event); // same eventId
    verify(orderService, times(1)).applyPaymentResult(orderId, true);
}
```

- [ ] **Step 2: Implement publishers after placeOrder/charge + idempotent listener → PASS**
- [ ] **Step 3: Commit** `feat: Kafka domain events with idempotent consumers`

---

### Task 9: RabbitMQ notifications + Spring Mail + Mailhog

**Files:**
- Modify: Compose — `rabbitmq:3-management`, `mailhog`
- Modify: `pom.xml` — `spring-boot-starter-amqp`, `spring-boot-starter-mail`
- Create: `notification/SendOrderConfirmationMessage.java`
- Create: `notification/OrderPaidNotificationPublisher.java` (Rabbit)
- Create: `notification/OrderConfirmationListener.java`
- Create: `notification/MailNotificationService.java`
- Create: `notification/RabbitConfig.java` (exchange, queue, DLQ, DLX)
- Create: listener test with Testcontainers RabbitMQ or `@SpringBootTest` + mock `JavaMailSender`

**Interfaces:**
- On order PAID (from `applyPaymentResult` or after sync charge success): publish to exchange `notifications` routing key `order.confirmation`
- Queue `order.confirmation` with DLQ `order.confirmation.dlq`
- `MailNotificationService.sendOrderConfirmation(toEmail, orderId, total)`

Mailhog: SMTP `localhost:1025`, UI `localhost:8025`.

- [ ] **Step 1: Failing test — paid order sends mail**

```java
@Test
void onOrderPaid_sendsEmail() {
    notificationService.onOrderPaid(buyerEmail, orderId, total);
    verify(mailSender).send(any(SimpleMailMessage.class));
}
```

- [ ] **Step 2: Implement AMQP + mail → PASS**
- [ ] **Step 3: Commit** `feat: RabbitMQ order confirmation emails via Mailhog`

---

### Task 10: Spring Batch reporting

**Files:**
- Modify: `pom.xml` — `spring-boot-starter-batch`, `spring-boot-starter-batch-jdbc` if needed
- Create: `V6__batch_and_reports.sql` (Spring Batch schema + `daily_sales_reports` table)
- Create: `reporting/DailySalesReportJobConfig.java`
- Create: `reporting/ReportingController.java` — `POST /api/v1/admin/reports/daily-sales` (`ADMIN`)
- Create: job unit/IT test

**Interfaces:**
- Job name `dailySalesReportJob`: read PAID/SHIPPED/DELIVERED orders for a date → write aggregate row `(report_date, order_count, total_amount)`
- Admin endpoint launches job via `JobLauncher`

- [ ] **Step 1: Failing test — job writes summary for seeded orders**
- [ ] **Step 2: Implement reader/processor/writer → PASS**
- [ ] **Step 3: Commit** `feat: Spring Batch daily sales report job`

---

### Task 11: Actuator security + admin order list

**Files:**
- Modify: `SecurityConfig` — `/actuator/health` public; `/actuator/**` else `ADMIN`; `/api/v1/admin/**` `ADMIN`
- Create: `order/AdminOrderController.java` — `GET /api/v1/admin/orders`
- Create: `V7__seed_admin.sql` optional — admin user with known bcrypt hash documented in README
- Create: security IT verifying buyer cannot hit admin

- [ ] **Step 1: Failing test — buyer GET admin orders → 403**
- [ ] **Step 2: Implement → PASS**
- [ ] **Step 3: Commit** `feat: admin order list and secured actuator`

---

### Task 12: React SPA scaffold + auth + catalog

**Files:**
- Create: `frontend/package.json`, `vite.config.ts`, `tsconfig.json`, `index.html`
- Create: `frontend/src/main.tsx`, `App.tsx`, `api/client.ts`, `auth/AuthContext.tsx`
- Create: pages `LoginPage`, `RegisterPage`, `CatalogPage`, `ProductDetailPage`
- Create: `frontend/.env.example` with `VITE_API_BASE_URL=http://localhost:8080`

**Interfaces:**
- `api/client.ts` attaches `Authorization: Bearer` from memory/localStorage
- On 401 from API, clear tokens and redirect to login

- [ ] **Step 1: Scaffold Vite React-TS app**

```bash
cd /Users/edmini/Desktop/SpringMegaProject
npm create vite@latest frontend -- --template react-ts
cd frontend && npm install && npm install react-router-dom
```

- [ ] **Step 2: Implement auth context + login/register forms calling `/api/v1/auth/*`**
- [ ] **Step 3: Catalog page lists `GET /api/v1/products`**
- [ ] **Step 4: Manual verify against running backend**
- [ ] **Step 5: Commit** `feat: React SPA with auth and catalog browse`

---

### Task 13: SPA cart, checkout, orders, seller, admin

**Files:**
- Create: `CartPage`, `CheckoutButton` flow, `OrdersPage`
- Create: `SellerProductPage` (create product + initial stock)
- Create: `AdminOrdersPage` + trigger report button
- Modify: nav by role from JWT claims or `/api/v1/auth/me` (add `GET /api/v1/auth/me` in backend if missing — small addition allowed here)

**Interfaces:**
- Buyer: add to cart → checkout `POST /api/v1/orders` → see orders
- Seller: create product
- Admin: list orders + `POST /api/v1/admin/reports/daily-sales`

- [ ] **Step 1: Implement pages + wire API**
- [ ] **Step 2: Manual E2E happy path (register buyer & seller, buy, see Mailhog)**
- [ ] **Step 3: Commit** `feat: SPA checkout, seller, and admin flows`

---

### Task 14: Testcontainers suite + README learning path

**Files:**
- Modify: `pom.xml` — Testcontainers BOM, modules for postgres/mongo/redis/kafka/rabbitmq/junit-jupiter
- Create: `backend/src/test/java/com/springmarket/support/IntegrationTestBase.java` with `@Testcontainers` + shared containers
- Create: one end-to-end IT `CheckoutFlowIT` (register → product → cart → order → payment event → mail mock)
- Create: `README.md` — architecture diagram, module map, how to run, Swagger/Mailhog URLs, learning path ordered by Tasks 1–13
- Modify: `docker-compose.yml` — ensure all services documented; optional `api`/`web` services can wait

- [ ] **Step 1: Write `CheckoutFlowIT` (failing until env green)**
- [ ] **Step 2: Implement `IntegrationTestBase` → PASS locally
- [ ] **Step 3: Write README**
- [ ] **Step 4: Commit** `test: Testcontainers checkout flow and project README`

---

## Self-review checklist (author)

| Spec requirement | Task |
|------------------|------|
| Modular monolith packages | 1–11 layout |
| Postgres + Flyway | 1–7, 10 |
| JWT + refresh + roles | 2, 11 |
| Mongo catalog + reviews | 4 |
| Redis cache | 4 |
| JPA cart | 5 |
| Orders + inventory transactions | 6 |
| Simulated payment + idempotency | 7 |
| Kafka domain events | 8 |
| Rabbit + Mail + Mailhog | 9 |
| Spring Batch reports | 10 |
| Actuator + OpenAPI | 1, 3, 11 |
| React SPA | 12–13 |
| Testcontainers + README | 14 |
| Review Focus 1–5 | Tasks 6, 7, 8, 2 |

No TBD placeholders remain; payment sync-then-events matches spec Phase 3/4; cart is JPA-only.

---

## Execution handoff

After plan approval, implement with either:

1. **Subagent-driven** — fresh subagent per task + reviewer gate (recommended for this plan)
2. **Native** — same agent session executes all tasks, one final review
