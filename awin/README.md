# Awin Transactions API

A small Spring Boot REST API that manages `Transaction` records, each composed of one or more `TransactionPart`s, and supports approving / declining them with safe concurrency and reliable event publishing via a transactional outbox.

## Requirements

- Java 21+ (tested with Amazon Corretto 23)
- Maven 3.9+ (any recent version works)

## Run

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21+)   # or any JDK 21+
mvn spring-boot:run
```

The app listens on `http://localhost:8080`. H2 console is available at `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:awin`, user `sa`, no password).

## Run tests

```bash
mvn test
```

## API

| Method | Path                          | Description                          |
|--------|-------------------------------|--------------------------------------|
| POST   | `/transactions`               | Create a `PENDING` transaction       |
| GET    | `/transactions/{id}`          | Fetch a transaction by id            |
| POST   | `/transactions/{id}/approve`  | Transition `PENDING` → `APPROVED`    |
| POST   | `/transactions/{id}/decline`  | Transition `PENDING` → `DECLINED`    |

Errors are returned as RFC 7807 `application/problem+json` documents.

### Example: create

```bash
curl -X POST http://localhost:8080/transactions \
  -H 'Content-Type: application/json' \
  -d '{
        "saleAmount": "100.00",
        "commissionAmount": "10.00",
        "parts": [
          {"saleAmount": "60.00", "commissionAmount": "6.00"},
          {"saleAmount": "40.00", "commissionAmount": "4.00"}
        ]
      }'
```

Response (`201 Created`):

```json
{
  "id": "1436105c-053b-4631-aeac-fb4d8bb27354",
  "status": "PENDING",
  "saleAmount": 100.00,
  "commissionAmount": 10.00,
  "parts": [
    {"id": "...", "transactionId": "1436105c-053b-4631-aeac-fb4d8bb27354", "saleAmount": 60.00, "commissionAmount": 6.00},
    {"id": "...", "transactionId": "1436105c-053b-4631-aeac-fb4d8bb27354", "saleAmount": 40.00, "commissionAmount": 4.00}
  ]
}
```

### Example: approve / decline

```bash
curl -X POST http://localhost:8080/transactions/<id>/approve
curl -X POST http://localhost:8080/transactions/<id>/decline
```

## Design notes

### Validation

`TransactionService.create` enforces:

- at least one part;
- `sum(parts.saleAmount) == transaction.saleAmount` (compared with `BigDecimal.compareTo`, so scale differences like `10` vs `10.00` are allowed);
- `sum(parts.commissionAmount) == transaction.commissionAmount`;
- all amounts non-negative;
- commission ≤ sale on both the transaction and each individual part.

Bean Validation (`@NotNull` / `@NotEmpty`) handles request-shape errors before domain validation runs.

### Concurrent updates

Two concurrent approve/decline requests on the same transaction must not both win.

**Choice: optimistic locking via JPA `@Version` on `Transaction`.**

- Each `approve` / `decline` runs in a `@Transactional` method that reads the row, mutates the status, and flushes.
- Hibernate increments `version` on flush; the losing transaction throws `OptimisticLockingFailureException` (mapped by the service to `ConcurrentModificationException` → HTTP `409 Conflict`).
- If a transaction is already in a terminal state when read, the domain throws `IllegalStateTransitionException` → HTTP `409 Conflict` (this is the typical second-attempt path).
- A dedicated test (`TransactionServiceConcurrencyTest`) spins up two threads racing on the same id and asserts that exactly one transitions, exactly one fails with a conflict, and exactly one outbox event is written.

Why not pessimistic locking (`SELECT … FOR UPDATE`)? It would also be correct, but it holds a row lock for the duration of the transaction, hurts throughput, and the conflict-on-second-write is a perfectly acceptable signal for a stateless REST client to retry or surface to the user. Optimistic locking is simpler to reason about and matches the "approve at most once" semantics naturally.

### Events and database/messaging consistency

The spec asks what happens if the database update succeeds but event publishing fails, or vice versa. The answer used here is the **transactional outbox** pattern:

1. When a status changes, the service writes both the updated `Transaction` row **and** an `OutboxEvent` row in the **same JPA transaction**. They commit or roll back together — there is no window in which the status changes without an event row being written.
2. A `@Scheduled` `OutboxPoller` (running every 1 second by default, in its own transaction) reads unpublished rows in `createdAt` order, hands each one to a `MessagePublisher`, and marks the row as published on success.
3. On publish failure (broker down, network blip), the row is left unpublished, `attempts` is incremented and `last_error` is recorded — the poller will retry on the next tick.
4. `MessagePublisher` is a one-method interface. The default `LoggingMessagePublisher` writes a structured log line, simulating a broker. A real Kafka / SQS / RabbitMQ implementation slots in by registering a different `MessagePublisher` bean.

### Trade-offs

- **At-least-once delivery.** If the poller publishes successfully but the JVM crashes before it commits the `publishedAt` update, the same event will be re-sent on the next run. Downstream consumers must be idempotent (the `OutboxEvent.id` makes a good dedup key).
- **Single-instance poller.** Two app instances would publish each event twice. In production I'd use `SELECT … FOR UPDATE SKIP LOCKED` (Postgres) to safely shard the poll, or run the poller as a leader-elected singleton.
- **Poison events.** A row that always fails to publish would have its `attempts` grow unbounded. A real implementation would add a max-attempts threshold + dead-letter table.
- **In-memory H2.** All state is lost on restart, which is fine for a take-home but obviously not for production — the outbox guarantees only work with durable storage.
- **No request-level idempotency.** A retried `POST /transactions/{id}/approve` after success returns `409`, not `200`. A real API would accept an `Idempotency-Key` header to make retries safe.
- **Eager fetch of parts.** Pragmatic for this scope so the controller can serialise responses outside the session. With larger aggregates I would project to DTOs inside the service / use entity graphs.

### What I would add with more time

- Kafka `MessagePublisher` implementation + a Testcontainers integration test.
- Postgres profile (`docker-compose`) and `SKIP LOCKED` polling.
- Flyway migrations.
- `Idempotency-Key` support on the create / approve / decline endpoints.
- OpenAPI / Swagger UI.
- Observability: Micrometer counters for poll batch size, publish failures, optimistic-lock conflicts.

## Project layout

```
src/main/java/com/awin/transactions/
  TransactionsApplication.java
  api/             REST controllers, DTOs, exception handler
  config/          Bean configuration (Clock)
  domain/          JPA entities, status enum, domain exceptions
  outbox/          OutboxEvent, repository, poller, MessagePublisher
  service/         TransactionService, validation, service exceptions
src/test/java/...  Controller, concurrency, and outbox poller tests
```

## AI usage

I used Claude (Anthropic) as a pair-programmer throughout this exercise.

**Where it helped**

- Initial planning: I described the task and asked it to enumerate edge cases (validation, state transitions, concurrency races, outbox failure modes). That edge-case list shaped the test suite and the README's trade-offs section.
- Scaffolding: drafting the `pom.xml`, the `application.yml`, and the package layout from a short spec.
- Boilerplate: generating the first version of the DTOs, the `GlobalExceptionHandler`, and the `BigDecimal` validation logic.
- Diagnosing two real issues we hit during the build: a `@ConditionalOnMissingBean` self-exclusion on the default `MessagePublisher`, and a `LazyInitializationException` on `Transaction.parts` once `open-in-view` was disabled.

**What I directed / changed**

- I chose the concurrency mechanism (optimistic via `@Version`) and the consistency mechanism (transactional outbox + poller) before any code was written, based on the spec's explicit questions. The assistant did not pick these for me.
- I rejected adding any abstraction beyond what the spec asked for (no `CommandBus`, no event-sourcing, no separate read model). The result is intentionally small.
- I reviewed every generated file before committing — in particular checking that the outbox write sits inside the same `@Transactional` boundary as the status update (it does), and that `BigDecimal` comparisons use `compareTo` rather than `equals` so that `10` and `10.00` are treated as equal sums (they are).
- The test design (a `CountDownLatch`-gated race for the concurrency assertion, a `RecordingMessagePublisher` with a `failNext` hook for the outbox failure case) was chosen by me — I find these patterns more reliable than thread sleeps.

I treated AI output as a draft to be challenged, not a finished answer. Where it suggested something I disagreed with (for example, an early draft used a CrudRepository with a custom locking query — overkill for this exercise) I reverted to a simpler approach.
