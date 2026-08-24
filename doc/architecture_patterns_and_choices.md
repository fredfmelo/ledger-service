# Architecture Patterns and Choices

This document describes the architectural decisions and recurring patterns used in **ledger-service**. It mirrors the fleet conventions established by **order-service**, with ledger-specific additions for immutable accounting and internal mutation APIs.

For package layout details, see [architecture_package_by_feature_documentation.md](./architecture_package_by_feature_documentation.md).

---

## 1. Overview

| Area | Choice |
|------|--------|
| Runtime | Java 21 |
| Framework | Spring Boot 3.5 |
| API style | REST, contract-first (OpenAPI) |
| Persistence | AWS DynamoDB (single-table design) |
| Messaging | AWS SNS (publish) + AWS SQS (consume) |
| Shared library | `event-driven-core` (outbox, idempotency, exceptions) |
| Build | Maven |
| Mapping | MapStruct |
| Boilerplate | Lombok |

The service is a **stateful microservice** that owns the accounting aggregate (wallet accounts, ledger transactions, ledger entries), exposes public wallet APIs and internal debit/credit APIs, publishes domain events, and prepares to react to `USER_CREATED`.

### Domain ownership

Ledger Service owns:

- Wallet accounts
- Immutable ledger entries
- Transactions
- Balance calculation (`Balance = Credits - Debits`)

Ledger Service does **not** own Orders, Payments, Products, or Inventory. Payment Service orchestrates money movement; this service records where money is stored.

---

## 2. Package by Feature (Vertical Slice)

The codebase is organized by **business capability**, not by technical layer.

```text
com.fredfmelo.ledgerservice
├── config/           # Cross-cutting Spring configuration
├── common/           # Shared utilities (exceptions, etc.)
├── security/         # Request-scoped auth context
├── api/              # Generated OpenAPI controller interfaces
├── model/            # Generated OpenAPI DTOs
├── ledger/           # Ledger feature (full vertical slice)
│   ├── controller/
│   ├── service/
│   ├── domain/
│   ├── repository/
│   ├── mapper/
│   ├── event/
│   ├── listener/
│   └── publisher/
└── LedgerServiceApplication.java
```

**Rule:** each feature owns its controller, services, persistence, events, and messaging adapters. Do not scatter one feature across global `controller/`, `service/`, and `repository/` folders.

---

## 3. Contract-First API (OpenAPI)

The HTTP contract is defined in `src/main/resources/openapi.yaml` and treated as the **source of truth**.

### Generation setup

The OpenAPI Generator Maven plugin runs at build time and produces:

| Output package | Contents | Editable? |
|----------------|----------|-----------|
| `api/` | Spring controller interfaces (`WalletApi`, `InternalApi`) | No — generated |
| `model/` | Request/response DTOs | No — generated |

Key generator options:

- `interfaceOnly=true`
- `useSpringBoot3=true`
- `useTags=true` — one interface per OpenAPI tag
- `delegatePattern=true`
- `openApiNullable=false`

### Controller pattern

Controllers are thin adapters that **implement generated interfaces** and delegate to services:

```java
@RestController
@RequiredArgsConstructor
public class WalletController implements WalletApi {

    private final LedgerCommandService ledgerCommandService;
    private final LedgerQueryService ledgerQueryService;
    private final UserContext userContext;

    @Override
    public ResponseEntity<WalletResponse> getWallet() {
        return ResponseEntity.ok(ledgerQueryService.getWallet(userContext));
    }
}
```

**Rules:**

- Never edit generated `api/` or `model/` code
- Add new endpoints by updating `openapi.yaml`, then rebuild
- Keep controllers free of business logic

### API URL convention

```yaml
server:
  servlet:
    context-path: /api/ledger-service/v1
```

Pattern: `/api/{service-name}/v{version}`

Port: `8088` (fleet assignment; order=`8080`, payment=`8081`, …).

### Public vs internal endpoints

| Surface | Paths | Exposed via API Gateway? |
|---------|-------|--------------------------|
| Public (`Wallet` tag) | `/wallet`, `/wallet/transactions`, … | Yes |
| Internal (`Internal` tag) | `/internal/accounts/{accountId}/credit\|debit` | **Never** |

Internal endpoints are consumed by trusted services (e.g. Payment Service). Gateway routing must exclude `/internal/**`.

---

## 4. Command / Query Separation (CQRS-lite)

| Service | Responsibility |
|---------|----------------|
| `LedgerCommandService` | Create wallet, credit, debit, handle inbound `USER_CREATED` |
| `LedgerQueryService` | Read wallet, statement/transactions, enforce ownership |

Event handlers stay on the command side. Query logic can evolve independently (pagination, filters, projections).

---

## 5. Domain Model vs API Model

| Layer | Package | Purpose |
|-------|---------|---------|
| Domain | `ledger/domain/` | Persistence entities, enums, business state |
| API | `model/` (generated) | HTTP request/response shapes |

Conversion happens in **feature mappers** (MapStruct).

**Rules:**

- Domain entities carry DynamoDB annotations (`@DynamoDbBean`, keys, GSIs)
- API DTOs carry OpenAPI/validation annotations
- Services work with domain entities; controllers expose API DTOs

### Domain entities

| Entity | Meaning |
|--------|---------|
| `WalletAccountEntity` | One wallet per user |
| `LedgerTransactionEntity` | Financial operation header (`TransactionType`) |
| `LedgerEntryEntity` | Immutable credit/debit line (`EntryType`, `amount`) |

### Guiding principles

- Ledger entries are **immutable** (append-only; never update amounts)
- Every financial operation creates a **transaction**
- Every transaction creates one or more **ledger entries**
- Balance is **derived**: `Credits - Debits` (may be cached later; entries remain authoritative)

---

## 6. Event-Driven Architecture

### 6.1 Shared `event-driven-core` library

- `Event` interface
- `OutboxService` / `OutboxEntity` / `OutboxEventPublisher`
- `IdempotentExecutor`
- `BusinessException` / `TechnicalException`

Repository: `https://maven.pkg.github.com/fredfmelo/event-driven-core`

### 6.2 Transactional Outbox

Credit/debit persist transaction + entry + outbox atomically:

```text
TransactWriteItems:
  ├── PUT LedgerTransactionEntity
  ├── PUT LedgerEntryEntity
  └── PUT OutboxEntity
```

Implementation: `ledger/repository/LedgerWriteRepository`.

### 6.3 Published events

| eventType | When |
|-----------|------|
| `WALLET_CREDITED` | Successful credit |
| `WALLET_DEBITED` | Successful debit |

### 6.4 Consumed events

| eventType | Handler | Status |
|-----------|---------|--------|
| `USER_CREATED` | Create wallet for user | Prepared (listener ready; Auth publishing is future) |

Until Auth publishes `USER_CREATED`, clients use temporary `POST /wallet`.

### 6.5 Publishing (SNS) / Consuming (SQS)

- `LedgerEventPublisher` implements `OutboxEventPublisher` → SNS topic `ledger-events`
- `LedgerQueueListener` `@SqsListener` on `ledger-queue` + `IdempotentExecutor`

---

## 7. DynamoDB Single-Table Design

Table name: `LEDGER`

| Entity | PK | SK |
|--------|----|----|
| Wallet account | `ACCOUNT#{accountId}` | `METADATA` |
| Transaction | `ACCOUNT#{accountId}` | `TX#{createdAt}#{transactionId}` |
| Entry | `ACCOUNT#{accountId}` | `ENTRY#{createdAt}#{entryId}` |
| Outbox / Idempotency | (managed by `event-driven-core`) | … |

### Global Secondary Indexes

| Index | Partition | Sort | Purpose |
|-------|-----------|------|---------|
| `user-accounts-index` | `userId` | `createdAt` | Resolve wallet by authenticated user |
| `transaction-id-index` | `transactionId` | — | Lookup transaction by id |

### Repository pattern

| Repository | Role |
|------------|------|
| `WalletAccountRepository` | Account CRUD / find by user |
| `LedgerTransactionRepository` | List/filter transactions |
| `LedgerEntryRepository` | List entries + balance calculation |
| `LedgerWriteRepository` | Atomic writes (account create; mutation + outbox) |

---

## 8. Configuration

### Typed properties

`ServiceConfig` binds root `@ConfigurationProperties` and implements `DynamoProperties` for `event-driven-core`.

### AWS beans

`AwsConfig` registers `DynamoDbClient`, `DynamoDbEnhancedClient`, `SnsClient`.

SQS is auto-configured by `spring-cloud-aws-starter-sqs`.

### Scheduling

`@EnableScheduling` on `LedgerServiceApplication` activates the outbox dispatcher.

### Key settings (`application.yaml`)

```yaml
server.port: 8088
server.servlet.context-path: /api/ledger-service/v1
aws.dynamodb.table-name: LEDGER
aws.sns.ledger-topic-arn: arn:aws:sns:us-east-1:200849340204:ledger-events
aws.sqs.ledger-queue: ledger-queue
```

---

## 9. Error Handling

`GlobalExceptionHandler` (`@RestControllerAdvice`) maps exceptions to:

```json
{
  "timestamp": "2026-07-29T12:00:00Z",
  "status": 409,
  "message": "Insufficient balance"
}
```

| Exception | When | HTTP status |
|-----------|------|-------------|
| `BusinessException` | Domain failures (not found, conflict, validation) | Custom per case |
| `TechnicalException` | Infra failures (SNS publish) | Typically 500 |
| `Exception` (fallback) | Unexpected | 500 |

Notable ledger statuses:

- `409` — wallet already exists; insufficient balance on debit
- `404` — wallet/account/transaction not found (ownership leaks as 404)

---

## 10. Security Context

Authentication is delegated to the API Gateway. Identity headers:

| Header | Maps to |
|--------|---------|
| `X-User-Id` | `UserContext.getUserId()` |
| `X-User-Email` | `UserContext.getEmail()` |
| `X-User-Role` | `UserContext.getRole()` |

Public wallet endpoints require `UserContext`. Internal debit/credit endpoints are account-id based and intended for service-to-service calls (gateway must not expose them).

---

## 11. Observability

- Spring Actuator health at `/actuator/health`
- `@Slf4j` on services, listeners, exception handler
- Trace IDs on every event record

---

## 12. Deployment

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Build: `./mvnw clean package` → run JAR or container.

---

## 13. Feature Package Anatomy

```text
ledger/
├── controller/       # WalletController, InternalAccountController
├── service/
│   ├── LedgerCommandService.java
│   └── LedgerQueryService.java
├── domain/           # DynamoDB entities and enums
├── repository/       # Data access + write repository
├── mapper/           # MapStruct: domain ↔ API DTOs
├── event/            # WALLET_CREDITED, WALLET_DEBITED, USER_CREATED
├── listener/         # SQS consumers
└── publisher/        # SNS OutboxEventPublisher
```

---

## 14. Blueprint Checklist (fleet alignment with order-service)

### Project setup

- [x] Spring Boot 3.5 + Java 21 Maven project
- [x] `event-driven-core` + GitHub Packages repository
- [x] AWS SDK v2 (DynamoDB, SNS, SQS) + Spring Cloud AWS SQS
- [x] MapStruct, Lombok, springdoc-openapi, actuator
- [x] OpenAPI Generator → `openapi.yaml`

### Structure

- [x] `config/`, `common/`, `security/`
- [x] Feature package `ledger/`
- [x] Generated-only `api/` and `model/`

### API

- [x] Contract-first OpenAPI
- [x] Context path `/api/ledger-service/v1`
- [x] Thin controllers implementing generated APIs
- [x] Public vs internal endpoint split (ledger-specific)

### Persistence

- [x] Single-table key schema + GSIs
- [x] `@DynamoDbBean` entities
- [x] Entity repositories + transactional write repository

### Events

- [x] Event records implementing `Event`
- [x] Outbox + transaction for publish-side consistency
- [x] `OutboxEventPublisher` for SNS
- [x] SQS listener with `IdempotentExecutor`

### Cross-cutting

- [x] `ServiceConfig` + `AwsConfig`
- [x] `GlobalExceptionHandler`
- [x] `UserContext`
- [x] `@EnableScheduling`

### Operations

- [x] Dockerfile (JRE 21)
- [x] Actuator health
- [x] AWS resource names in `application.yaml`

---

## 15. Architecture Diagram

```text
                    ┌──────────────┐
                    │  API Gateway │
                    │  (JWT → hdr) │
                    └──────┬───────┘
                           │ HTTP (public /wallet only)
                           ▼
              ┌────────────────────────┐
              │     ledger-service     │
              │                        │
              │  WalletController      │
              │  InternalAccountCtrl   │◄── Payment Service (internal HTTP)
              │       │                │
              │       ▼                │
              │   DynamoDB LEDGER      │
              │   (account/tx/entry    │
              │    + outbox)           │
              │       │                │
              │       ▼                │
              │   Outbox dispatcher    │
              └────────┬───────────────┘
                       │ SNS
                       ▼
              ┌────────────────────────┐
              │   ledger-events topic  │
              └────────┬───────────────┘
                       │
                       ▼
                 (subscribers)

  Future: Auth USER_CREATED → ledger-queue → create wallet
```

---

## 16. References

| Concept | Applied as |
|---------|------------|
| Package by Feature | `ledger/` vertical slice |
| Contract-First API | OpenAPI YAML → generated interfaces/DTOs |
| Transactional Outbox | Atomic write of mutation + outbox |
| CQRS (lite) | Command vs query services |
| Single-Table Design | Composite PK/SK + GSIs |
| Idempotent Consumer | `IdempotentExecutor` on SQS handlers |
| Immutable Ledger | Append-only entries; derived balance |
| Public / Internal API | Gateway-facing vs trusted service paths |

Goal: services that are **easy to navigate**, **safe under failure**, and **consistent** across the fleet.
