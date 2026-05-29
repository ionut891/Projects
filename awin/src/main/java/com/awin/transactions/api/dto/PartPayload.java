package com.awin.transactions.api.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PartPayload(@NotNull BigDecimal saleAmount, @NotNull BigDecimal commissionAmount) {}
