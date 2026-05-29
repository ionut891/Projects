package com.awin.transactions.api;

import com.awin.transactions.api.dto.CreateTransactionRequest;
import com.awin.transactions.api.dto.TransactionResponse;
import com.awin.transactions.domain.Transaction;
import com.awin.transactions.service.PartInput;
import com.awin.transactions.service.TransactionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody CreateTransactionRequest request) {
        Transaction created = service.create(
                request.saleAmount(),
                request.commissionAmount(),
                request.parts().stream()
                        .map(p -> new PartInput(p.saleAmount(), p.commissionAmount()))
                        .toList());
        return ResponseEntity.created(URI.create("/transactions/" + created.getId()))
                .body(TransactionResponse.from(created));
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@PathVariable UUID id) {
        return TransactionResponse.from(service.get(id));
    }

    @PostMapping("/{id}/approve")
    public TransactionResponse approve(@PathVariable UUID id) {
        return TransactionResponse.from(service.approve(id));
    }

    @PostMapping("/{id}/decline")
    public TransactionResponse decline(@PathVariable UUID id) {
        return TransactionResponse.from(service.decline(id));
    }
}
