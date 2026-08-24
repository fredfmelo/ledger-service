package com.fredfmelo.ledgerservice.ledger.domain;

import java.time.Instant;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@Getter
@Setter
@DynamoDbBean
public class WalletAccountEntity {

    public static final String USER_ACCOUNTS_INDEX = "user-accounts-index";
    public static final String ENTITY_TYPE = "WALLET_ACCOUNT";
    public static final String DEFAULT_CURRENCY = "BRL";

    private String pk;
    private String sk;
    private String entityType;
    private UUID accountId;
    private UUID userId;
    private String currency;
    private Instant createdAt;

    @DynamoDbPartitionKey
    public String getPk() {
        return pk;
    }

    @DynamoDbSortKey
    public String getSk() {
        return sk;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = USER_ACCOUNTS_INDEX)
    public UUID getUserId() {
        return userId;
    }

    @DynamoDbSecondarySortKey(indexNames = USER_ACCOUNTS_INDEX)
    public Instant getCreatedAt() {
        return createdAt;
    }
}
