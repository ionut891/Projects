package com.awin.transactions.service;

import java.math.BigDecimal;

/**
 * Service-layer input for a single {@link com.awin.transactions.domain.TransactionPart}, decoupled
 * from the API DTOs so the service does not depend on the web tier.
 */
public record PartInput(BigDecimal saleAmount, BigDecimal commissionAmount) {}
