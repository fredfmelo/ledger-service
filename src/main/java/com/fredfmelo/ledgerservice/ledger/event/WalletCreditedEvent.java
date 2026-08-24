package com.fredfmelo.ledgerservice.ledger.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fredfmelo.eventdrivencore.event.Event;

public record WalletCreditedEvent(
        UUID eventId,
        String traceId,
        String eventType,
        Instant occurredAt,
        UUID accountId,
        UUID userId,
        UUID transactionId,
        BigDecimal amount,
        BigDecimal balance,
        String currency,
        String transactionType) implements Event {
}
