# Architecture Decision Record: Package Structure

## Decision

This project uses **Package by Feature (Vertical Slice)** instead of a global layer-based structure.

Avoid:

```text
controller/
service/
repository/
dto/
entity/
```

Use:

```text
ledger/
```

Each feature owns its internal structure.

---

## Why

Traditional layer-based organization becomes hard to maintain as the project grows:

```text
service/
 ├── OrderService
 ├── PaymentService
 ├── InventoryService
 ├── LedgerService
 └── ProductService
```

Problems:

- Large folders with unrelated files
- High navigation cost
- Features spread across many directories
- More coupling
- Harder ownership and maintenance

Package by Feature keeps related code physically close.

---

## Project Structure

```text
src/main/java/com/fredfmelo/ledgerservice

├── config/
│   ├── AwsConfig.java
│   └── ServiceConfig.java
│
├── common/
│   └── exception/
│       ├── GlobalExceptionHandler.java
│       └── ErrorResponse.java
│
├── security/
│   └── UserContext.java
│
├── ledger/
│   ├── controller/
│   │   ├── WalletController.java
│   │   └── InternalAccountController.java
│   ├── service/
│   │   ├── LedgerCommandService.java
│   │   └── LedgerQueryService.java
│   ├── domain/
│   │   ├── WalletAccountEntity.java
│   │   ├── LedgerTransactionEntity.java
│   │   ├── LedgerEntryEntity.java
│   │   ├── EntryType.java
│   │   ├── TransactionType.java
│   │   └── Role.java
│   ├── repository/
│   │   ├── WalletAccountRepository.java
│   │   ├── LedgerTransactionRepository.java
│   │   ├── LedgerEntryRepository.java
│   │   └── LedgerWriteRepository.java
│   ├── mapper/
│   │   └── LedgerMapper.java
│   ├── event/
│   │   ├── WalletCreditedEvent.java
│   │   ├── WalletDebitedEvent.java
│   │   └── UserCreatedEvent.java
│   ├── listener/
│   │   └── LedgerQueueListener.java
│   └── publisher/
│       └── LedgerEventPublisher.java
│
└── LedgerServiceApplication.java
```

Generated at build time (not edited by hand):

```text
api/     # WalletApi, InternalApi
model/   # OpenAPI DTOs
```

---

## Rules

1. New business capability → new feature package (same internal layout).
2. Cross-cutting concerns stay in `config/`, `common/`, `security/`.
3. Controllers implement generated OpenAPI interfaces only.
4. Persistence and messaging adapters live inside the feature that owns them.
5. Do not introduce a global `infrastructure/` package unless shared by multiple features in this service.

---

## Alignment

This ADR matches the package-by-feature decision used in **order-service** and sibling marketplace services.
