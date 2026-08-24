package com.fredfmelo.ledgerservice.ledger.domain;

import java.time.Instant;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@Getter
@Setter
@DynamoDbBean
public class LedgerTransactionEntity {

    public static final String TRANSACTION_ID_INDEX = "transaction-id-index";
    public static final String ENTITY_TYPE = "LEDGER_TRANSACTION";

    private String pk;
    private String sk;
    private String entityType;
    private UUID transactionId;
    private UUID accountId;
    private TransactionType type;
    private String description;
    private Instant createdAt;

    @DynamoDbPartitionKey
    public String getPk() {
        return pk;
    }

    @DynamoDbSortKey
    public String getSk() {
        return sk;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = TRANSACTION_ID_INDEX)
    public UUID getTransactionId() {
        return transactionId;
    }
}
