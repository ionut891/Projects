package com.awin.transactions.service;

import java.util.UUID;

/**
 * Raised when the optimistic lock on {@link com.awin.transactions.domain.Transaction} detects that
 * another request mutated the same row concurrently. Mapped to HTTP {@code 409}; clients may
 * re-read state and decide whether to retry.
 */
public class ConcurrentModificationException extends RuntimeException {
    public ConcurrentModificationException(UUID id) {
        super("Transaction " + id + " was modified concurrently");
    }
}
