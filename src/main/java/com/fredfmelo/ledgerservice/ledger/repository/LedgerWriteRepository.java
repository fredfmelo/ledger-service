package com.fredfmelo.ledgerservice.ledger.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import com.fredfmelo.eventdrivencore.outbox.entity.OutboxEntity;
import com.fredfmelo.ledgerservice.config.ServiceConfig;
import com.fredfmelo.ledgerservice.ledger.domain.LedgerEntryEntity;
import com.fredfmelo.ledgerservice.ledger.domain.LedgerTransactionEntity;
import com.fredfmelo.ledgerservice.ledger.domain.WalletAccountEntity;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;

@Repository
@RequiredArgsConstructor
public class LedgerWriteRepository {

    private final DynamoDbClient dynamoDbClient;
    private final ServiceConfig serviceConfig;

    public void saveAccount(WalletAccountEntity account) {
        dynamoDbClient.transactWriteItems(TransactWriteItemsRequest.builder()
                .transactItems(List.of(buildPut(account, WalletAccountEntity.class)))
                .build());
    }

    public void saveMutation(
            LedgerTransactionEntity transaction,
            LedgerEntryEntity entry,
            OutboxEntity outbox) {

        List<TransactWriteItem> transactionItems = new ArrayList<>();
        transactionItems.add(buildPut(transaction, LedgerTransactionEntity.class));
        transactionItems.add(buildPut(entry, LedgerEntryEntity.class));
        transactionItems.add(buildPut(outbox, OutboxEntity.class));

        dynamoDbClient.transactWriteItems(TransactWriteItemsRequest.builder()
                .transactItems(transactionItems)
                .build());
    }

    private <T> TransactWriteItem buildPut(T entity, Class<T> clazz) {
        Map<String, AttributeValue> item = TableSchema.fromBean(clazz).itemToMap(entity, true);

        return TransactWriteItem.builder()
                .put(Put.builder()
                        .tableName(serviceConfig.getAws().getDynamodb().getTableName())
                        .item(item)
                        .build())
                .build();
    }
}
