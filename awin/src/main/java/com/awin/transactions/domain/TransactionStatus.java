package com.awin.transactions.domain;

/**
 * Lifecycle state of a {@link Transaction}.
 *
 * <p>A transaction is created in {@link #PENDING} and may transition exactly once to either
 * {@link #APPROVED} or {@link #DECLINED}; both are terminal.
 */
public enum TransactionStatus {
    PENDING,
    APPROVED,
    DECLINED;

    /**
     * @return {@code true} if this status is terminal (cannot transition further).
     */
    public boolean isTerminal() {
        return this != PENDING;
    }
}
