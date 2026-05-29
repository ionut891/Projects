package com.awin.transactions.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate root for a tracked sale event. A {@code Transaction} is composed of one or more
 * {@link TransactionPart}s whose amounts must sum to the parent's totals; that invariant is
 * enforced at construction time by the service layer.
 *
 * <p>Status follows the {@link TransactionStatus} state machine. {@link #approve()} and
 * {@link #decline()} are the only legal mutations and may each be called at most once across the
 * lifetime of the row.
 *
 * <p>Concurrent updates are serialised through the {@link Version JPA optimistic lock} on
 * {@link #version}: if two transactions race to mutate the same row, the loser fails on flush with
 * an {@code OptimisticLockingFailureException}.
 */
@Entity
@Table(name = "transaction")
public class Transaction {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransactionStatus status;

    @Column(name = "sale_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal saleAmount;

    @Column(name = "commission_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal commissionAmount;

    @Version
    private Long version;

    @OneToMany(
            mappedBy = "transaction",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER)
    private List<TransactionPart> parts = new ArrayList<>();

    protected Transaction() {
        // for JPA
    }

    public Transaction(BigDecimal saleAmount, BigDecimal commissionAmount, List<TransactionPart> parts) {
        this.status = TransactionStatus.PENDING;
        this.saleAmount = saleAmount;
        this.commissionAmount = commissionAmount;
        for (TransactionPart part : parts) {
            addPart(part);
        }
    }

    private void addPart(TransactionPart part) {
        part.setTransaction(this);
        this.parts.add(part);
    }

    /**
     * Move the transaction from {@link TransactionStatus#PENDING} to
     * {@link TransactionStatus#APPROVED}.
     *
     * @throws IllegalStateTransitionException if the transaction is already in a terminal status.
     */
    public void approve() {
        transitionTo(TransactionStatus.APPROVED);
    }

    /**
     * Move the transaction from {@link TransactionStatus#PENDING} to
     * {@link TransactionStatus#DECLINED}.
     *
     * @throws IllegalStateTransitionException if the transaction is already in a terminal status.
     */
    public void decline() {
        transitionTo(TransactionStatus.DECLINED);
    }

    private void transitionTo(TransactionStatus next) {
        if (status.isTerminal()) {
            throw new IllegalStateTransitionException(this.id, this.status, next);
        }
        this.status = next;
    }

    public UUID getId() {
        return id;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public BigDecimal getSaleAmount() {
        return saleAmount;
    }

    public BigDecimal getCommissionAmount() {
        return commissionAmount;
    }

    public Long getVersion() {
        return version;
    }

    public List<TransactionPart> getParts() {
        return Collections.unmodifiableList(parts);
    }
}
