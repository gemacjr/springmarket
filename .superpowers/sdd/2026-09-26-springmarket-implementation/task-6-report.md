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

## Round 1/5 Quality Fixes

### Findings addressed

1. **Concurrent same-cart checkout**
   - Added a `PESSIMISTIC_WRITE` cart lookup dedicated to checkout.
   - `OrderService.placeOrder` now obtains the cart through that lookup inside its existing transaction.
   - The cart row remains locked through inventory reservation, order persistence, and cart deletion.
   - A concurrent second request waits for the first transaction and then receives the domain error `Cart is empty`; it cannot create another order from the consumed cart.

2. **Late failed payment after PAID**
   - `applyPaymentResult` now requires the current status to be exactly `PENDING` before applying either success or failure.
   - A late failure for a `PAID` order raises `InvalidOrderStateException`, leaves the order `PAID`, and does not release inventory.
   - The general `PAID -> CANCELLED` state-machine rule remains unchanged for a future explicit cancellation workflow.

### RED evidence

Command:

```text
./mvnw -DargLine=-Dnet.bytebuddy.experimental=true \
  -Dtest=OrderServiceTest,ConcurrentCheckoutTest test
```

The late-payment regression failed as expected:

```text
Expected InvalidOrderStateException to be thrown, but nothing was thrown.
Tests run: 7, Failures: 1, Errors: 0, Skipped: 0
BUILD FAILURE
```

The initial concurrent assertion only counted commits and exposed an incidental delete conflict. It was tightened to require the intended loser outcome. Against the original implementation:

```text
Unexpected type, expected: DomainException
but was: ObjectOptimisticLockingFailureException
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
BUILD FAILURE
```

This confirmed that checkout was not serializing access to the cart.

### GREEN covering tests

Command:

```text
./mvnw -DargLine=-Dnet.bytebuddy.experimental=true \
  -Dtest=OrderServiceTest,ConcurrentCheckoutTest,CartServiceTest test
```

Result:

```text
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The concurrency test uses two real `OrderService` transactions and real JPA cart/order repositories. Inventory is mocked with a bounded rendezvous so both requests reliably overlap. It asserts one committed order and requires the losing request to receive `DomainException("Cart is empty")`.

Controller regression command:

```text
./mvnw -DargLine=-Dnet.bytebuddy.experimental=true \
  -Dtest=OrderControllerIT,CatalogControllerIT,CartControllerIT test
```

Result:

```text
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Full regression command, run by itself after an earlier pair of simultaneous Maven commands interfered through their shared `target/` directory:

```text
./mvnw -DargLine=-Dnet.bytebuddy.experimental=true test
```

Authoritative sequential result:

```text
Tests run: 31, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
