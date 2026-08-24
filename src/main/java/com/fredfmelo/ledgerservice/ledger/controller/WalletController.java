package com.fredfmelo.ledgerservice.ledger.controller;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.fredfmelo.ledgerservice.api.WalletApi;
import com.fredfmelo.ledgerservice.ledger.service.LedgerCommandService;
import com.fredfmelo.ledgerservice.ledger.service.LedgerQueryService;
import com.fredfmelo.ledgerservice.model.TransactionPageResponse;
import com.fredfmelo.ledgerservice.model.TransactionResponse;
import com.fredfmelo.ledgerservice.model.TransactionTypeApi;
import com.fredfmelo.ledgerservice.model.WalletResponse;
import com.fredfmelo.ledgerservice.security.UserContext;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class WalletController implements WalletApi {

    private final LedgerCommandService ledgerCommandService;
    private final LedgerQueryService ledgerQueryService;
    private final UserContext userContext;

    @Override
    public ResponseEntity<WalletResponse> createWallet() {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ledgerCommandService.createWallet(userContext));
    }

    @Override
    public ResponseEntity<WalletResponse> getWallet() {
        return ResponseEntity.ok(ledgerQueryService.getWallet(userContext));
    }

    @Override
    public ResponseEntity<TransactionPageResponse> getWalletTransactions(
            Integer page,
            Integer size,
            TransactionTypeApi type,
            OffsetDateTime from,
            OffsetDateTime to) {
        return ResponseEntity.ok(ledgerQueryService.getTransactions(userContext, page, size, type, from, to));
    }

    @Override
    public ResponseEntity<TransactionResponse> getWalletTransaction(UUID transactionId) {
        return ResponseEntity.ok(ledgerQueryService.getTransaction(userContext, transactionId));
    }
}
