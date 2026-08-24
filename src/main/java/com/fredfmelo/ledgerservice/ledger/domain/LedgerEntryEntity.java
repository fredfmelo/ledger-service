package com.fredfmelo.ledgerservice.ledger.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@Getter
@Setter
@DynamoDbBean
public class LedgerEntryEntity {

    public static final String ENTITY_TYPE = "LEDGER_ENTRY";

    private String pk;
    private String sk;
    private String entityType;
    private UUID entryId;
    private UUID transactionId;
    private UUID accountId;
    private EntryType entryType;
    private BigDecimal amount;
    private Instant createdAt;

    @DynamoDbPartitionKey
    public String getPk() {
        return pk;
    }

    @DynamoDbSortKey
    public String getSk() {
        return sk;
    }
}
