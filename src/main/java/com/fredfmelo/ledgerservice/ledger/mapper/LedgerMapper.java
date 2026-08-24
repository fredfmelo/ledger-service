package com.fredfmelo.ledgerservice.ledger.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.fredfmelo.ledgerservice.ledger.domain.EntryType;
import com.fredfmelo.ledgerservice.ledger.domain.LedgerEntryEntity;
import com.fredfmelo.ledgerservice.ledger.domain.LedgerTransactionEntity;
import com.fredfmelo.ledgerservice.ledger.domain.TransactionType;
import com.fredfmelo.ledgerservice.ledger.domain.WalletAccountEntity;
import com.fredfmelo.ledgerservice.model.EntryTypeApi;
import com.fredfmelo.ledgerservice.model.TransactionResponse;
import com.fredfmelo.ledgerservice.model.TransactionTypeApi;
import com.fredfmelo.ledgerservice.model.WalletResponse;

@Mapper(componentModel = "spring")
public interface LedgerMapper {

    @Mapping(target = "balance", source = "balance")
    WalletResponse toWalletResponse(WalletAccountEntity account, BigDecimal balance);

    @Mapping(target = "amount", source = "entry.amount")
    @Mapping(target = "entryType", source = "entry.entryType")
    @Mapping(target = "transactionId", source = "transaction.transactionId")
    @Mapping(target = "type", source = "transaction.type")
    @Mapping(target = "description", source = "transaction.description")
    @Mapping(target = "createdAt", source = "transaction.createdAt")
    TransactionResponse toTransactionResponse(LedgerTransactionEntity transaction, LedgerEntryEntity entry);

    EntryTypeApi map(EntryType entryType);

    TransactionTypeApi map(TransactionType transactionType);

    default OffsetDateTime map(Instant instant) {
        return instant == null
                ? null
                : instant.atOffset(ZoneOffset.UTC);
    }

    default List<TransactionResponse> toTransactionResponses(
            List<LedgerTransactionEntity> transactions,
            List<LedgerEntryEntity> entries) {

        return transactions.stream()
                .map(tx -> {
                    LedgerEntryEntity entry = entries.stream()
                            .filter(e -> tx.getTransactionId().equals(e.getTransactionId()))
                            .findFirst()
                            .orElse(null);
                    return toTransactionResponse(tx, entry);
                })
                .toList();
    }
}
