package com.fredfmelo.ledgerservice.ledger.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fredfmelo.eventdrivencore.exception.BusinessException;
import com.fredfmelo.ledgerservice.ledger.domain.LedgerEntryEntity;
import com.fredfmelo.ledgerservice.ledger.domain.LedgerTransactionEntity;
import com.fredfmelo.ledgerservice.ledger.domain.TransactionType;
import com.fredfmelo.ledgerservice.ledger.domain.WalletAccountEntity;
import com.fredfmelo.ledgerservice.ledger.mapper.LedgerMapper;
import com.fredfmelo.ledgerservice.ledger.repository.LedgerEntryRepository;
import com.fredfmelo.ledgerservice.ledger.repository.LedgerTransactionRepository;
import com.fredfmelo.ledgerservice.ledger.repository.WalletAccountRepository;
import com.fredfmelo.ledgerservice.model.TransactionPageResponse;
import com.fredfmelo.ledgerservice.model.TransactionResponse;
import com.fredfmelo.ledgerservice.model.TransactionTypeApi;
import com.fredfmelo.ledgerservice.model.WalletResponse;
import com.fredfmelo.ledgerservice.security.UserContext;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class LedgerQueryService {

    private final WalletAccountRepository walletAccountRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerMapper ledgerMapper;

    public WalletResponse getWallet(UserContext userContext) {
        WalletAccountEntity account = findOwnedAccount(userContext.getUserId());
        BigDecimal balance = ledgerEntryRepository.calculateBalance(account.getAccountId());
        return ledgerMapper.toWalletResponse(account, balance);
    }

    public TransactionPageResponse getTransactions(
            UserContext userContext,
            Integer page,
            Integer size,
            TransactionTypeApi type,
            OffsetDateTime from,
            OffsetDateTime to) {

        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? 20 : size;

        WalletAccountEntity account = findOwnedAccount(userContext.getUserId());

        TransactionType transactionType = type == null ? null : TransactionType.valueOf(type.getValue());
        Instant fromInstant = from == null ? null : from.toInstant();
        Instant toInstant = to == null ? null : to.toInstant();

        List<LedgerTransactionEntity> transactions = ledgerTransactionRepository.findByAccountId(
                account.getAccountId(), transactionType, fromInstant, toInstant);

        long totalElements = transactions.size();
        int fromIndex = Math.min(pageNumber * pageSize, transactions.size());
        int toIndex = Math.min(fromIndex + pageSize, transactions.size());
        List<LedgerTransactionEntity> pageContent = transactions.subList(fromIndex, toIndex);

        List<LedgerEntryEntity> entries = ledgerEntryRepository.findByAccountId(account.getAccountId());
        List<TransactionResponse> content = ledgerMapper.toTransactionResponses(pageContent, entries);

        return new TransactionPageResponse()
                .page(pageNumber)
                .size(pageSize)
                .totalElements(totalElements)
                .content(content);
    }

    public TransactionResponse getTransaction(UserContext userContext, UUID transactionId) {
        WalletAccountEntity account = findOwnedAccount(userContext.getUserId());

        LedgerTransactionEntity transaction = ledgerTransactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new BusinessException("Transaction not found", HttpStatus.NOT_FOUND.value()));

        if (!account.getAccountId().equals(transaction.getAccountId())) {
            throw new BusinessException("Transaction not found", HttpStatus.NOT_FOUND.value());
        }

        LedgerEntryEntity entry = ledgerEntryRepository
                .findByTransactionId(account.getAccountId(), transactionId)
                .stream()
                .findFirst()
                .orElse(null);

        return ledgerMapper.toTransactionResponse(transaction, entry);
    }

    private WalletAccountEntity findOwnedAccount(UUID userId) {
        return walletAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("Wallet not found", HttpStatus.NOT_FOUND.value()));
    }
}
