# SpringMarket — Design Spec

**Date:** 2026-09-26  
**Status:** Draft for review  
**Goal:** A cohesive marketplace application that teaches major Spring technologies through one realistic domain, not disconnected demos.

---

## 1. Intent & success criteria

### What we are building

**SpringMarket** — a learning-oriented marketplace where:

- **Buyers** browse products, manage a cart, checkout, and view orders.
- **Sellers** create and manage their own products.
- **Admins** manage users, view all orders, and trigger reporting jobs.

### Success looks like

- One runnable system (`docker compose up` + API + React SPA) that exercises Spring end-to-end.
- Each major Spring topic appears inside a real feature (not a toy isolated sample).
- Clear package boundaries so a learner can study one module at a time.
- Phased delivery: a working core first, then messaging, secondary stores, Batch, and SPA polish.

### Explicit non-goals (v1)

- Real payment provider integration (Stripe, etc.) — payments are simulated.
- Full Spring Cloud microservices (Gateway, Eureka, Config Server) — deferred to an optional later phase.
- Production-grade storefront UX — SPA stays thin and functional.
- Multi-region / multi-tenant SaaS complexity.

---

## 2. Architecture

### Shape

**Modular monolith** — a single Spring Boot application with package-level bounded contexts, plus a separate React SPA in the same repository.

```
┌─────────────┐     REST/JWT      ┌──────────────────────────────┐
│ React SPA   │ ───────────────►  │  Spring Boot modular monolith │
│ (Vite + TS) │                   │  identity | catalog | cart    │
└─────────────┘                   │  order | payment | inventory  │
                                  │  notification | reporting     │
                                  └──────┬───────┬───────┬───────┘
                     Postgres + Flyway ──┘       │       │
                     MongoDB ────────────────────┘       │
                     Redis ──────────────────────────────┘
                                  │
                    ┌─────────────┴─────────────┐
                    │ RabbitMQ      │  Kafka      │
                    │ (commands /   │  (domain    │
                    │  notifications)│  events)   │
                    └───────────────┴─────────────┘
```

### Runtime dependencies (Docker Compose)

| Service | Role |
|---------|------|
| Postgres | System of record: users, carts, orders, inventory, payments, refresh tokens |
| MongoDB | Catalog documents and product reviews |
| Redis | Hot product listing cache; optional cart session hints / rate-limit counters |
| RabbitMQ | Operational commands (e.g. send email) with retry + DLQ |
| Kafka | Durable domain event stream (`OrderPlaced`, `PaymentCompleted`, …) |
| Mailhog | Local SMTP catcher for Spring Mail |
| API (optional in Compose) | Spring Boot app |
| SPA (optional in Compose) | Nginx or Vite preview serving the React build |

### API conventions

- Base path: `/api/v1/...`
- OpenAPI via springdoc; Swagger UI for exploration
- Actuator: health, info, metrics (secured appropriately)
- Problem Details (`application/problem+json`) for error responses

### Authentication & authorization

- Spring Security with **JWT access tokens**
- **Refresh tokens** stored hashed in Postgres; rotation on refresh
- Password hashing: BCrypt
- Roles: `BUYER`, `SELLER`, `ADMIN`
- CORS restricted to the SPA origin
- Secrets via environment / `.env` (never committed)

**Access rules (summary):**

| Area | Access |
|------|--------|
| Register, login, catalog browse | Public |
| Cart, checkout, own orders | Authenticated |
| Product CRUD (own products) | `SELLER` |
| Users, all orders, Batch triggers | `ADMIN` |

---

## 3. Domain modules

Cross-module coupling rules:

- Prefer **application service APIs** or **domain events** over reaching into another module’s repositories.
- Shared kernel is minimal (e.g. `Money`, `UserId` value types) — no god `utils` package.

| Module | Responsibility | Primary Spring tech |
|--------|----------------|---------------------|
| `identity` | Register, login, refresh, user profiles, roles | Security, Data JPA, Validation |
| `catalog` | Products, categories, reviews, search/list | Data Mongo + JPA refs to sellers; Redis cache |
| `cart` | Per-user cart lines; price snapshot at add-time | Data JPA (Postgres); Redis not used as cart store in v1 |
| `order` | Place order; status machine; own-order queries | JPA transactions; Kafka produce/consume |
| `payment` | Simulated charge; idempotency keys | Kafka events; optional Rabbit for async charge |
| `inventory` | Stock levels; reserve/release on order lifecycle | JPA + transactional consistency with orders |
| `notification` | Order/payment confirmation emails | Spring AMQP (Rabbit) + Spring Mail |
| `reporting` | Sales/inventory summaries | Spring Batch; scheduled or admin-triggered |

### Frontend (`frontend/`)

Thin React + Vite + TypeScript client:

- Auth (register/login)
- Catalog browse + product detail
- Cart + checkout
- Buyer order history
- Seller product form
- Admin order list + “run report” action

Not a polished e-commerce brand site — enough UI to drive every API path while learning.

---

## 4. Key data flows

### 4.1 Checkout (happy path)

1. Buyer `POST /api/v1/orders` from current cart.
2. **order** + **inventory** in one Postgres transaction:
   - Create `Order` with status `PENDING`
   - Reserve stock
   - Clear cart
3. After commit, publish Kafka event `OrderPlaced` (include `eventId`, `orderId`, `buyerId`, line items, totals).
4. **payment** processes a simulated charge:
   - **Phase 3:** synchronous call from order flow (still emits Kafka outcome events for learning).
   - **Phase 4+:** optionally driven by messaging; behavior and idempotency rules stay the same.
   - Honor **idempotency key**
   - Emit Kafka `PaymentCompleted` or `PaymentFailed`
5. **order** consumes payment result:
   - Success → `PAID`
   - Failure → `CANCELLED` + release inventory
6. On `PAID`, publish Rabbit message `SendOrderConfirmation` → **notification** sends email through Mailhog.

### 4.2 Catalog read path

1. SPA requests product list.
2. API checks Redis; on miss loads from Mongo (and seller metadata as needed), populates cache, returns DTO.

### 4.3 Why two brokers

| Broker | Purpose |
|--------|---------|
| **Kafka** | Domain event log — what happened in the business (`OrderPlaced`, `PaymentCompleted`) |
| **RabbitMQ** | Operational work — do this task now (`SendOrderConfirmation`) with retries and DLQ |

### 4.4 Reporting

- Spring Batch job (nightly schedule and/or `ADMIN` trigger) reads order data from Postgres and writes summary tables and/or CSV under a configured reports directory.

### 4.5 Order status machine

```
PENDING → PAID → SHIPPED → DELIVERED
    └────→ CANCELLED  (payment failure or admin cancel before ship)
```

Illegal transitions throw domain exceptions → Problem Details responses.

---

## 5. Data model (logical)

### Postgres (Flyway-migrated)

- `users` — id, email, password_hash, roles, timestamps
- `refresh_tokens` — id, user_id, token_hash, expires_at, revoked
- `carts` / `cart_items` — user_id, product_id, quantity, unit_price_snapshot
- `orders` / `order_items` — status, totals, buyer_id, timestamps
- `inventory` — product_id, available_qty, reserved_qty
- `payments` — order_id, status, idempotency_key, amount, timestamps
- `report_summaries` (or similar) — Batch output

### MongoDB

- `products` — sellerId, name, description, price, category, attributes, active
- `reviews` — productId, buyerId, rating, body, createdAt

Exact column/field lists are refined during implementation; Flyway is the source of truth for relational schema.

---

## 6. Error handling

- Domain exceptions: `InsufficientStockException`, `InvalidOrderStateException`, `ProductNotFoundException`, etc.
- Global `@ControllerAdvice` maps to RFC 7807 Problem Details.
- Bean Validation on request DTOs → 400 with field-level errors.
- Rabbit consumers: retry then **dead-letter queue**; log and alert via Actuator/logs.
- Kafka consumers: **idempotent** processing keyed by `eventId` (processed-events table or equivalent).
- Payment double-submit: same idempotency key returns the original result.

---

## 7. Project layout (target)

```
SpringMegaProject/
├── backend/                 # Spring Boot (Maven)
│   └── src/main/java/.../springmarket/
│       ├── identity/
│       ├── catalog/
│       ├── cart/
│       ├── order/
│       ├── payment/
│       ├── inventory/
│       ├── notification/
│       ├── reporting/
│       └── shared/          # minimal shared kernel
├── frontend/                # React + Vite + TypeScript
├── docker-compose.yml
├── docs/superpowers/
│   ├── specs/
│   └── plans/
└── README.md
```

**Suggested stack versions (pinned in implementation plan):**

- Java 21 LTS
- Spring Boot 3.4.x (or latest 3.x stable at implementation time)
- React 19 + Vite + TypeScript
- Build: Maven

---

## 8. Testing strategy

| Layer | Approach |
|-------|----------|
| Unit | Domain services and status transitions |
| Slice | `@DataJpaTest`, `@DataMongoTest`, `@WebMvcTest` (+ Security) |
| Integration | Testcontainers for Postgres, Mongo, Redis, RabbitMQ, Kafka |
| SPA | Deferred smoke tests; not a blocker for backend phases |
| Manual | Swagger UI + Mailhog UI + Compose logs |

CI may run a lighter Testcontainers subset if full broker matrix is too slow; document which profile is “full local.”

---

## 9. Delivery phases

Implementation will be planned in phases so the app is always runnable:

1. **Foundation** — Boot app, Docker Compose (Postgres), Flyway, identity (JWT), OpenAPI, Actuator skeleton
2. **Catalog + cart** — Mongo products/reviews, Redis cache, cart APIs
3. **Order + inventory + payment (sync path)** — transactional checkout, simulated payment
4. **Messaging** — Kafka domain events; Rabbit + Mail notifications
5. **Reporting** — Spring Batch job + admin trigger
6. **SPA** — React client covering core buyer/seller/admin flows
7. **Hardening** — Testcontainers suite, idempotency polish, DLQ, README learning path

Phases may overlap slightly in the implementation plan but must preserve a working vertical slice early.

---

## 10. Optional future phase (out of scope now)

Extract modules into separate services behind Spring Cloud Gateway, with shared auth and event-driven integration — only after the monolith learning path is solid.

---

## 11. Decisions log

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Domain | Marketplace / e-commerce | Natural fit for JPA, transactions, roles, events, Batch |
| Architecture | Modular monolith | Cohesive learning without distributed-ops tax |
| Scope | Full learning platform | Postgres, Mongo, Redis, Rabbit, Kafka, Batch, Mail, Actuator, OpenAPI |
| Messaging | RabbitMQ + Kafka | Commands/notifications vs domain event log |
| UI | React + Vite + TypeScript SPA | Common pairing with Spring APIs; keep UI thin |
| Auth | JWT + refresh in Postgres | Teaches Security filter chain and token lifecycle |
| Payments | Simulated | Focus on Spring patterns, not PSP SDKs |
| Build tool | Maven | Widely used in Spring docs and tutorials |
| Cart storage | Postgres/JPA | Clear transactional story alongside orders |
