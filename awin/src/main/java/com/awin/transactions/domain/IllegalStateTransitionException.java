package com.awin.transactions.domain;

import java.util.UUID;

public class IllegalStateTransitionException extends RuntimeException {

    private final UUID transactionId;
    private final TransactionStatus from;
    private final TransactionStatus to;

    public IllegalStateTransitionException(UUID transactionId, TransactionStatus from, TransactionStatus to) {
        super("Cannot transition transaction " + transactionId + " from " + from + " to " + to);
        this.transactionId = transactionId;
        this.from = from;
        this.to = to;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public TransactionStatus getFrom() {
        return from;
    }

    public TransactionStatus getTo() {
        return to;
    }
}
