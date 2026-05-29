package com.awin.transactions.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * A constituent line of a {@link Transaction}. A transaction's {@code saleAmount} and
 * {@code commissionAmount} must equal the sum of the corresponding fields across all of its parts;
 * this is enforced by the service layer on creation.
 */
@Entity
@Table(name = "transaction_part")
public class TransactionPart {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Column(name = "sale_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal saleAmount;

    @Column(name = "commission_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal commissionAmount;

    protected TransactionPart() {
        // for JPA
    }

    public TransactionPart(BigDecimal saleAmount, BigDecimal commissionAmount) {
        this.saleAmount = saleAmount;
        this.commissionAmount = commissionAmount;
    }

    public UUID getId() {
        return id;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public BigDecimal getSaleAmount() {
        return saleAmount;
    }

    public BigDecimal getCommissionAmount() {
        return commissionAmount;
    }
}
