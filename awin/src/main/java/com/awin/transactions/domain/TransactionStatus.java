package com.awin.transactions.domain;

public enum TransactionStatus {
    PENDING,
    APPROVED,
    DECLINED;

    public boolean isTerminal() {
        return this != PENDING;
    }
}
