package com.fredfmelo.ledgerservice.common.exception;

import java.time.Instant;

public record ErrorResponse(
        Instant timestamp,
        Integer status,
        String message
) {
}
