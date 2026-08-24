package com.fredfmelo.ledgerservice.ledger.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.fredfmelo.ledgerservice.config.ServiceConfig;
import com.fredfmelo.ledgerservice.ledger.domain.EntryType;
import com.fredfmelo.ledgerservice.ledger.domain.LedgerEntryEntity;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

@Repository
@RequiredArgsConstructor
public class LedgerEntryRepository {

    private final ServiceConfig serviceConfig;
    private final DynamoDbEnhancedClient client;

    private DynamoDbTable<LedgerEntryEntity> table() {
        return client.table(serviceConfig.getAws().getDynamodb().getTableName(),
                TableSchema.fromBean(LedgerEntryEntity.class));
    }

    public List<LedgerEntryEntity> findByAccountId(UUID accountId) {
        QueryConditional query = QueryConditional.sortBeginsWith(
                Key.builder()
                        .partitionValue("ACCOUNT#" + accountId)
                        .sortValue("ENTRY#")
                        .build());

        return table().query(r -> r.queryConditional(query))
                .items()
                .stream()
                .toList();
    }

    public List<LedgerEntryEntity> findByTransactionId(UUID accountId, UUID transactionId) {
        return findByAccountId(accountId).stream()
                .filter(entry -> transactionId.equals(entry.getTransactionId()))
                .toList();
    }

    public BigDecimal calculateBalance(UUID accountId) {
        BigDecimal credits = BigDecimal.ZERO;
        BigDecimal debits = BigDecimal.ZERO;

        for (LedgerEntryEntity entry : findByAccountId(accountId)) {
            if (entry.getEntryType() == EntryType.CREDIT) {
                credits = credits.add(entry.getAmount());
            } else if (entry.getEntryType() == EntryType.DEBIT) {
                debits = debits.add(entry.getAmount());
            }
        }

        return credits.subtract(debits);
    }
}
