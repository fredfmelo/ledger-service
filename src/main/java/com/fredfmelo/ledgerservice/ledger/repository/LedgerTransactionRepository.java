package com.fredfmelo.ledgerservice.ledger.repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.fredfmelo.ledgerservice.config.ServiceConfig;
import com.fredfmelo.ledgerservice.ledger.domain.LedgerTransactionEntity;
import com.fredfmelo.ledgerservice.ledger.domain.TransactionType;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

@Repository
@RequiredArgsConstructor
public class LedgerTransactionRepository {

    private final ServiceConfig serviceConfig;
    private final DynamoDbEnhancedClient client;

    private DynamoDbTable<LedgerTransactionEntity> table() {
        return client.table(serviceConfig.getAws().getDynamodb().getTableName(),
                TableSchema.fromBean(LedgerTransactionEntity.class));
    }

    public List<LedgerTransactionEntity> findByAccountId(UUID accountId) {
        QueryConditional query = QueryConditional.sortBeginsWith(
                Key.builder()
                        .partitionValue("ACCOUNT#" + accountId)
                        .sortValue("TX#")
                        .build());

        return table().query(r -> r.queryConditional(query))
                .items()
                .stream()
                .sorted(Comparator.comparing(LedgerTransactionEntity::getCreatedAt).reversed())
                .toList();
    }

    public List<LedgerTransactionEntity> findByAccountId(
            UUID accountId,
            TransactionType type,
            Instant from,
            Instant to) {

        return findByAccountId(accountId).stream()
                .filter(tx -> type == null || tx.getType() == type)
                .filter(tx -> from == null || !tx.getCreatedAt().isBefore(from))
                .filter(tx -> to == null || !tx.getCreatedAt().isAfter(to))
                .toList();
    }

    public Optional<LedgerTransactionEntity> findByTransactionId(UUID transactionId) {
        DynamoDbIndex<LedgerTransactionEntity> index =
                table().index(LedgerTransactionEntity.TRANSACTION_ID_INDEX);

        QueryConditional query = QueryConditional.keyEqualTo(
                Key.builder()
                        .partitionValue(transactionId.toString())
                        .build());

        return index.query(r -> r.queryConditional(query))
                .stream()
                .flatMap(page -> page.items().stream())
                .findFirst();
    }
}
