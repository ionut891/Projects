package com.awin.transactions.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record CreateTransactionRequest(
    @NotNull BigDecimal saleAmount,
    @NotNull BigDecimal commissionAmount,
    @NotEmpty @Valid List<PartPayload> parts) {}
