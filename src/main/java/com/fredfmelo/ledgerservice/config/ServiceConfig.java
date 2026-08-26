package com.fredfmelo.ledgerservice.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.fredfmelo.eventdrivencore.config.DynamoProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties
public class ServiceConfig implements DynamoProperties {

    private Aws aws;

    @Getter
    @Setter
    public static class Aws {
        private DynamoDb dynamodb;
        private Sns sns;
    }

    @Getter
    @Setter
    public static class DynamoDb {
        private String tableName;
    }

    @Getter
    @Setter
    public static class Sns {
        private String ledgerTopicArn;
    }

    private Wallet wallet = new Wallet();

    @Getter
    @Setter
    public static class Wallet {
        private BigDecimal initialBalance = BigDecimal.ZERO;
    }

    @Override
    public String tableName() {
        return aws.getDynamodb().getTableName();
    }
}
