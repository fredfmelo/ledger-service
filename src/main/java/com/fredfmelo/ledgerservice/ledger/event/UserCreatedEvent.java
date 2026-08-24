package com.fredfmelo.ledgerservice.ledger.event;

import java.time.Instant;
import java.util.UUID;

import com.fredfmelo.eventdrivencore.event.Event;

public record UserCreatedEvent(
        UUID eventId,
        String traceId,
        String eventType,
        Instant occurredAt,
        UUID userId) implements Event {
}
