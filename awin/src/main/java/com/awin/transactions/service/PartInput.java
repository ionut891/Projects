package com.awin.transactions.service;

import java.math.BigDecimal;

public record PartInput(BigDecimal saleAmount, BigDecimal commissionAmount) {}
