package com.awin.transactions.service;

import java.util.UUID;

/**
 * Thrown when a lookup or mutation targets a transaction id that does not exist. Mapped to HTTP
 * {@code 404}.
 */
public class TransactionNotFoundException extends RuntimeException {
    public TransactionNotFoundException(UUID id) {
        super("Transaction not found: " + id);
    }
}
