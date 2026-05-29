package com.awin.transactions.service;

import java.util.UUID;

public class ConcurrentModificationException extends RuntimeException {
    public ConcurrentModificationException(UUID id) {
        super("Transaction " + id + " was modified concurrently");
    }
}
