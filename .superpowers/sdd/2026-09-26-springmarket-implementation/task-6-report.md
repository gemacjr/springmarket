# Task 6 Report: Order + Inventory

## Result

Implemented transactional checkout with Postgres/Flyway V3 persistence, optimistic inventory locking, order status transitions, ownership-scoped order reads, and product stock initialization.

`placeOrder` creates a `PENDING` order, reserves every cart line, persists the order and its price snapshots, and clears the cart inside one `@Transactional` service method. No payment or messaging dependency was wired. `applyPaymentResult` is available for Task 7: success transitions `PENDING -> PAID`; failure transitions `PENDING -> CANCELLED` and releases each reserved line.

## Implementation

- Added the brief's V3 SQL verbatim for `inventory_items`, `orders`, and `order_items`.
- Added `InventoryItem` with JPA `@Version`, plus initialize/reserve/release operations.
- Normalized insufficient quantity, absent inventory, and a lost optimistic-lock race to `InsufficientStockException` (HTTP 409 through the existing `DomainException` handler).
- Added order aggregate, items, repository, response DTOs, service, and `/api/v1/orders` controller.
- Added status machine:
  - `PENDING -> PAID | CANCELLED`
  - `PAID -> SHIPPED | CANCELLED`
  - `SHIPPED -> DELIVERED`
  - terminal `DELIVERED` and `CANCELLED`
- Illegal transitions throw `InvalidOrderStateException` (HTTP 400).
- `GET /api/v1/orders/{id}` uses an `id + buyerId` repository lookup so callers can only retrieve their own orders.
- Added required `initialStock` validation to `CreateProductRequest`; catalog creation calls `inventoryService.initialize(saved.id(), initialStock)`.

## TDD Evidence

### RED: review-focus behaviors

Command:

```text
./mvnw -DargLine=-Dnet.bytebuddy.experimental=true \
  -Dtest=OrderStatusMachineTest,InventoryServiceTest,OrderServiceTest test
```

Observed `BUILD FAILURE` during test compilation because the requested APIs did not exist: `InventoryService`, `InsufficientStockException`, `OrderService`, `OrderRepository`, `OrderStatusMachine`, `OrderStatus`, and `InvalidOrderStateException`.

Tests were added first with the brief's required names and assertions:

- `placeOrder_whenInsufficientStock_throws`
- `concurrentReserve_onlyOneSucceeds`
- `illegalTransition_deliveredToPending_rejected`

### GREEN: review-focus behaviors

The same command passed after the minimal implementation:

```text
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The concurrent test uses two executor threads against the real Spring transaction proxy and H2 persistence context. Exactly one reserves the last unit; the other receives `InsufficientStockException`.

### RED/GREEN: API and catalog contracts

Tests were added for authenticated place/mine/get-own routes and for catalog inventory initialization. The RED run failed because `OrderController`, the new catalog dependency, and `CreateProductRequest.initialStock` did not yet exist. After implementation:

```text
Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

This targeted run included `CatalogServiceTest`, `CatalogControllerIT`, `OrderServiceTest`, and `OrderControllerIT`.

### Full regression

Command:

```text
./mvnw -DargLine=-Dnet.bytebuddy.experimental=true test
```

Result:

```text
Tests run: 28, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Maven Surefire's default naming includes `*Test` but not `*IT`; controller integration tests were therefore also run explicitly in the targeted API command above.

### Concurrency stability

`InventoryServiceTest` was run in five consecutive isolated Maven invocations:

```text
for i in 1 2 3 4 5; do
  ./mvnw -q -DargLine=-Dnet.bytebuddy.experimental=true \
    -Dtest=InventoryServiceTest test || exit 1
done
```

All five runs passed.

## Notes

- Java 25 required the supplied Byte Buddy experimental flag for Mockito-based tests.
- Test startup logs an existing non-fatal MongoDB connection-refused monitor warning because the inventory integration test boots the application context; no Mongo operation is performed and the test passes.
- Payment charging, Kafka, and RabbitMQ wiring remain intentionally deferred to Tasks 7-8.
