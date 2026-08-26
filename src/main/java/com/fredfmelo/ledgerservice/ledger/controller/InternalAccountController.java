package com.fredfmelo.ledgerservice.ledger.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.fredfmelo.ledgerservice.api.InternalApi;
import com.fredfmelo.ledgerservice.ledger.service.LedgerCommandService;
import com.fredfmelo.ledgerservice.ledger.service.LedgerQueryService;
import com.fredfmelo.ledgerservice.model.LedgerMutationRequest;
import com.fredfmelo.ledgerservice.model.LedgerMutationResponse;
import com.fredfmelo.ledgerservice.model.WalletResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class InternalAccountController implements InternalApi {

    private final LedgerCommandService ledgerCommandService;
    private final LedgerQueryService ledgerQueryService;

    @Override
    public ResponseEntity<WalletResponse> getWalletByUserId(UUID userId) {
        return ResponseEntity.ok(ledgerQueryService.getWalletByUserId(userId));
    }

    @Override
    public ResponseEntity<LedgerMutationResponse> creditAccount(
            UUID accountId,
            LedgerMutationRequest ledgerMutationRequest) {
        return ResponseEntity.ok(ledgerCommandService.credit(accountId, ledgerMutationRequest));
    }

    @Override
    public ResponseEntity<LedgerMutationResponse> debitAccount(
            UUID accountId,
            LedgerMutationRequest ledgerMutationRequest) {
        return ResponseEntity.ok(ledgerCommandService.debit(accountId, ledgerMutationRequest));
    }
}
