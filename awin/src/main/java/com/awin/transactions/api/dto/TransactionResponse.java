package com.awin.transactions.api.dto;

import com.awin.transactions.domain.Transaction;
import com.awin.transactions.domain.TransactionStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        TransactionStatus status,
        BigDecimal saleAmount,
        BigDecimal commissionAmount,
        List<Part> parts) {

    public record Part(UUID id, BigDecimal saleAmount, BigDecimal commissionAmount) {}

    public static TransactionResponse from(Transaction tx) {
        List<Part> parts = tx.getParts().stream()
                .map(p -> new Part(p.getId(), p.getSaleAmount(), p.getCommissionAmount()))
                .toList();
        return new TransactionResponse(
                tx.getId(), tx.getStatus(), tx.getSaleAmount(), tx.getCommissionAmount(), parts);
    }
}
