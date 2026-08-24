package com.fredfmelo.ledgerservice.ledger.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.fredfmelo.ledgerservice.config.ServiceConfig;
import com.fredfmelo.ledgerservice.ledger.domain.WalletAccountEntity;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

@Repository
@RequiredArgsConstructor
public class WalletAccountRepository {

    private final ServiceConfig serviceConfig;
    private final DynamoDbEnhancedClient client;

    private DynamoDbTable<WalletAccountEntity> table() {
        return client.table(serviceConfig.getAws().getDynamodb().getTableName(),
                TableSchema.fromBean(WalletAccountEntity.class));
    }

    public void save(WalletAccountEntity entity) {
        table().putItem(entity);
    }

    public Optional<WalletAccountEntity> findByAccountId(UUID accountId) {
        Key key = Key.builder()
                .partitionValue("ACCOUNT#" + accountId)
                .sortValue("METADATA")
                .build();

        return Optional.ofNullable(table().getItem(key));
    }

    public Optional<WalletAccountEntity> findByUserId(UUID userId) {
        DynamoDbIndex<WalletAccountEntity> index = table().index(WalletAccountEntity.USER_ACCOUNTS_INDEX);

        QueryConditional query = QueryConditional.keyEqualTo(
                Key.builder()
                        .partitionValue(userId.toString())
                        .build());

        List<WalletAccountEntity> accounts = index.query(r -> r.queryConditional(query))
                .stream()
                .flatMap(page -> page.items().stream())
                .toList();

        return accounts.stream().findFirst();
    }
}
