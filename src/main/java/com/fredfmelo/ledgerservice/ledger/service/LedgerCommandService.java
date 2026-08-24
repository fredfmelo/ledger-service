package com.fredfmelo.ledgerservice.ledger.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fredfmelo.eventdrivencore.event.Event;
import com.fredfmelo.eventdrivencore.exception.BusinessException;
import com.fredfmelo.eventdrivencore.outbox.entity.OutboxEntity;
import com.fredfmelo.eventdrivencore.outbox.service.OutboxService;
import com.fredfmelo.ledgerservice.ledger.domain.EntryType;
import com.fredfmelo.ledgerservice.ledger.domain.LedgerEntryEntity;
import com.fredfmelo.ledgerservice.ledger.domain.LedgerTransactionEntity;
import com.fredfmelo.ledgerservice.ledger.domain.TransactionType;
import com.fredfmelo.ledgerservice.ledger.domain.WalletAccountEntity;
import com.fredfmelo.ledgerservice.ledger.event.WalletCreditedEvent;
import com.fredfmelo.ledgerservice.ledger.event.WalletDebitedEvent;
import com.fredfmelo.ledgerservice.ledger.mapper.LedgerMapper;
import com.fredfmelo.ledgerservice.ledger.repository.LedgerEntryRepository;
import com.fredfmelo.ledgerservice.ledger.repository.LedgerWriteRepository;
import com.fredfmelo.ledgerservice.ledger.repository.WalletAccountRepository;
import com.fredfmelo.ledgerservice.model.EntryTypeApi;
import com.fredfmelo.ledgerservice.model.LedgerMutationRequest;
import com.fredfmelo.ledgerservice.model.LedgerMutationResponse;
import com.fredfmelo.ledgerservice.model.WalletResponse;
import com.fredfmelo.ledgerservice.security.UserContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class LedgerCommandService {

    private final WalletAccountRepository walletAccountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerWriteRepository ledgerWriteRepository;
    private final OutboxService outboxService;
    private final LedgerMapper ledgerMapper;

    public WalletResponse createWallet(UserContext userContext) {
        return createWalletForUser(userContext.getUserId());
    }

    public WalletResponse createWalletForUser(UUID userId) {
        walletAccountRepository.findByUserId(userId).ifPresent(existing -> {
            throw new BusinessException("Wallet already exists for user", HttpStatus.CONFLICT.value());
        });

        WalletAccountEntity account = buildAccount(userId);
        ledgerWriteRepository.saveAccount(account);

        return ledgerMapper.toWalletResponse(account, BigDecimal.ZERO);
    }

    public LedgerMutationResponse credit(UUID accountId, LedgerMutationRequest request) {
        return mutate(accountId, request, EntryType.CREDIT);
    }

    public LedgerMutationResponse debit(UUID accountId, LedgerMutationRequest request) {
        return mutate(accountId, request, EntryType.DEBIT);
    }

    private LedgerMutationResponse mutate(
            UUID accountId,
            LedgerMutationRequest request,
            EntryType entryType) {

        validateMutation(request);

        WalletAccountEntity account = walletAccountRepository.findByAccountId(accountId)
                .orElseThrow(() -> new BusinessException("Account not found", HttpStatus.NOT_FOUND.value()));

        BigDecimal amount = request.getAmount();
        BigDecimal currentBalance = ledgerEntryRepository.calculateBalance(accountId);

        if (entryType == EntryType.DEBIT && currentBalance.compareTo(amount) < 0) {
            throw new BusinessException("Insufficient balance", HttpStatus.CONFLICT.value());
        }

        Instant now = Instant.now();
        UUID transactionId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        TransactionType transactionType = TransactionType.valueOf(request.getType().getValue());

        LedgerTransactionEntity transaction = buildTransaction(
                accountId, transactionId, transactionType, request.getDescription(), now);
        LedgerEntryEntity entry = buildEntry(accountId, transactionId, entryId, entryType, amount, now);

        BigDecimal newBalance = entryType == EntryType.CREDIT
                ? currentBalance.add(amount)
                : currentBalance.subtract(amount);

        Event event = entryType == EntryType.CREDIT
                ? buildCreditedEvent(account, transactionId, amount, newBalance, transactionType)
                : buildDebitedEvent(account, transactionId, amount, newBalance, transactionType);

        OutboxEntity outbox = outboxService.buildEntity(event);
        ledgerWriteRepository.saveMutation(transaction, entry, outbox);

        return new LedgerMutationResponse()
                .transactionId(transactionId)
                .accountId(accountId)
                .type(request.getType())
                .entryType(EntryTypeApi.fromValue(entryType.name()))
                .amount(amount)
                .balance(newBalance)
                .currency(account.getCurrency())
                .createdAt(now.atOffset(java.time.ZoneOffset.UTC));
    }

    private void validateMutation(LedgerMutationRequest request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Amount must be greater than zero");
        }

        if (request.getType() == null) {
            throw new BusinessException("Transaction type is required");
        }
    }

    private WalletAccountEntity buildAccount(UUID userId) {
        UUID accountId = UUID.randomUUID();
        Instant now = Instant.now();

        WalletAccountEntity account = new WalletAccountEntity();
        account.setPk("ACCOUNT#" + accountId);
        account.setSk("METADATA");
        account.setEntityType(WalletAccountEntity.ENTITY_TYPE);
        account.setAccountId(accountId);
        account.setUserId(userId);
        account.setCurrency(WalletAccountEntity.DEFAULT_CURRENCY);
        account.setCreatedAt(now);
        return account;
    }

    private LedgerTransactionEntity buildTransaction(
            UUID accountId,
            UUID transactionId,
            TransactionType type,
            String description,
            Instant createdAt) {

        LedgerTransactionEntity transaction = new LedgerTransactionEntity();
        transaction.setPk("ACCOUNT#" + accountId);
        transaction.setSk("TX#" + createdAt + "#" + transactionId);
        transaction.setEntityType(LedgerTransactionEntity.ENTITY_TYPE);
        transaction.setTransactionId(transactionId);
        transaction.setAccountId(accountId);
        transaction.setType(type);
        transaction.setDescription(description);
        transaction.setCreatedAt(createdAt);
        return transaction;
    }

    private LedgerEntryEntity buildEntry(
            UUID accountId,
            UUID transactionId,
            UUID entryId,
            EntryType entryType,
            BigDecimal amount,
            Instant createdAt) {

        LedgerEntryEntity entry = new LedgerEntryEntity();
        entry.setPk("ACCOUNT#" + accountId);
        entry.setSk("ENTRY#" + createdAt + "#" + entryId);
        entry.setEntityType(LedgerEntryEntity.ENTITY_TYPE);
        entry.setEntryId(entryId);
        entry.setTransactionId(transactionId);
        entry.setAccountId(accountId);
        entry.setEntryType(entryType);
        entry.setAmount(amount);
        entry.setCreatedAt(createdAt);
        return entry;
    }

    private WalletCreditedEvent buildCreditedEvent(
            WalletAccountEntity account,
            UUID transactionId,
            BigDecimal amount,
            BigDecimal balance,
            TransactionType type) {

        return new WalletCreditedEvent(
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                "WALLET_CREDITED",
                Instant.now(),
                account.getAccountId(),
                account.getUserId(),
                transactionId,
                amount,
                balance,
                account.getCurrency(),
                type.name());
    }

    private WalletDebitedEvent buildDebitedEvent(
            WalletAccountEntity account,
            UUID transactionId,
            BigDecimal amount,
            BigDecimal balance,
            TransactionType type) {

        return new WalletDebitedEvent(
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                "WALLET_DEBITED",
                Instant.now(),
                account.getAccountId(),
                account.getUserId(),
                transactionId,
                amount,
                balance,
                account.getCurrency(),
                type.name());
    }
}
