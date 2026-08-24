package com.fredfmelo.ledgerservice.ledger.listener;

import java.util.List;

import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fredfmelo.eventdrivencore.idempotency.executor.IdempotentExecutor;
import com.fredfmelo.ledgerservice.ledger.event.UserCreatedEvent;
import com.fredfmelo.ledgerservice.ledger.service.LedgerCommandService;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerQueueListener {

    private static final String EVENT_TYPE = "eventType";

    private final ObjectMapper objectMapper;
    private final IdempotentExecutor idempotentExecutor;
    private final LedgerCommandService ledgerCommandService;

    @SqsListener("${aws.sqs.ledger-queue}")
    public void consume(List<Message<String>> messages) {
        for (Message<String> message : messages) {
            handleMessage(message.getPayload());
        }
    }

    private void handleMessage(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventType = root.get(EVENT_TYPE).asText();

            switch (eventType) {
                case "USER_CREATED" -> {
                    UserCreatedEvent event = objectMapper.readValue(payload, UserCreatedEvent.class);
                    idempotentExecutor.execute(event, () -> ledgerCommandService.createWalletForUser(event.userId()));
                }
                default -> log.warn("Unsupported event type: {}", eventType);
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to process ledger event", e);
        }
    }
}
