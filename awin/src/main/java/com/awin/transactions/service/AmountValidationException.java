package com.awin.transactions.service;

/**
 * Thrown when an amount invariant is violated on transaction creation (mismatched sums, negative
 * amounts, commission greater than sale, or empty parts list). Mapped to HTTP {@code 400}.
 */
public class AmountValidationException extends RuntimeException {
    public AmountValidationException(String message) {
        super(message);
    }
}
