package com.awin.transactions.service;

import com.awin.transactions.domain.Transaction;
import com.awin.transactions.domain.TransactionPart;
import com.awin.transactions.domain.TransactionRepository;
import com.awin.transactions.outbox.OutboxEvent;
import com.awin.transactions.outbox.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service that coordinates the {@link Transaction} aggregate with the transactional
 * outbox.
 *
 * <p>{@link #approve(UUID)} and {@link #decline(UUID)} run inside a single JPA transaction that
 * (a) updates the row's status and (b) inserts a corresponding {@link OutboxEvent}, so the event
 * record is durable iff the state change is. A separate {@code OutboxPoller} drains the outbox to
 * the configured {@link com.awin.transactions.outbox.MessagePublisher}.
 *
 * <p>Concurrency is handled optimistically: if two requests race to mutate the same transaction,
 * Hibernate detects the version mismatch on flush and the loser is reported to the caller as
 * {@link ConcurrentTransactionUpdateException} (mapped to HTTP {@code 409}).
 */
@Service
public class TransactionService {

    private final TransactionRepository transactions;
    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TransactionService(
            TransactionRepository transactions,
            OutboxRepository outbox,
            ObjectMapper objectMapper,
            Clock clock) {
        this.transactions = transactions;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Create a new {@link Transaction} in {@link com.awin.transactions.domain.TransactionStatus#PENDING}.
     *
     * @param saleAmount       the total sale value of the transaction.
     * @param commissionAmount the total commission of the transaction.
     * @param partInputs       at least one part; the sums of {@code saleAmount} and
     *                         {@code commissionAmount} across all parts must equal the
     *                         transaction-level values.
     * @return the persisted transaction.
     * @throws AmountValidationException if any of the amount invariants fail (sums mismatch,
     *                                   negative amounts, commission greater than sale, or an
     *                                   empty parts list).
     */
    @Transactional
    public Transaction create(BigDecimal saleAmount, BigDecimal commissionAmount, List<PartInput> partInputs) {
        validateAmounts(saleAmount, commissionAmount, partInputs);

        List<TransactionPart> parts = partInputs.stream()
                .map(p -> new TransactionPart(p.saleAmount(), p.commissionAmount()))
                .toList();

        Transaction tx = new Transaction(saleAmount, commissionAmount, parts);
        return transactions.save(tx);
    }

    /**
     * Transition a transaction from {@code PENDING} to {@code APPROVED} and write the
     * {@code TransactionApproved} outbox event in the same JPA transaction.
     *
     * @param id transaction id.
     * @return the updated transaction.
     * @throws TransactionNotFoundException        if no transaction exists with this id.
     * @throws com.awin.transactions.domain.IllegalStateTransitionException
     *                                             if the transaction is not in {@code PENDING}.
     * @throws ConcurrentTransactionUpdateException     if a concurrent request mutated the transaction
     *                                             first.
     */
    @Transactional
    public Transaction approve(UUID id) {
        Transaction tx = load(id);
        tx.approve();
        return persistAndEmit(tx, "TransactionApproved");
    }

    /**
     * Transition a transaction from {@code PENDING} to {@code DECLINED} and write the
     * {@code TransactionDeclined} outbox event in the same JPA transaction.
     *
     * @param id transaction id.
     * @return the updated transaction.
     * @throws TransactionNotFoundException        if no transaction exists with this id.
     * @throws com.awin.transactions.domain.IllegalStateTransitionException
     *                                             if the transaction is not in {@code PENDING}.
     * @throws ConcurrentTransactionUpdateException     if a concurrent request mutated the transaction
     *                                             first.
     */
    @Transactional
    public Transaction decline(UUID id) {
        Transaction tx = load(id);
        tx.decline();
        return persistAndEmit(tx, "TransactionDeclined");
    }

    /**
     * Look up a transaction by id.
     *
     * @throws TransactionNotFoundException if no transaction exists with this id.
     */
    @Transactional(readOnly = true)
    public Transaction get(UUID id) {
        return load(id);
    }

    private Transaction load(UUID id) {
        return transactions.findById(id).orElseThrow(() -> new TransactionNotFoundException(id));
    }

    private Transaction persistAndEmit(Transaction tx, String eventType) {
        try {
            Transaction saved = transactions.saveAndFlush(tx);
            outbox.save(new OutboxEvent(
                    saved.getId(),
                    eventType,
                    serialize(saved, eventType),
                    clock.instant()));
            return saved;
        } catch (OptimisticLockingFailureException | OptimisticLockException e) {
            throw new ConcurrentTransactionUpdateException(tx.getId());
        }
    }

    private String serialize(Transaction tx, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", eventType);
        payload.put("transactionId", tx.getId().toString());
        payload.put("status", tx.getStatus().name());
        payload.put("saleAmount", tx.getSaleAmount());
        payload.put("commissionAmount", tx.getCommissionAmount());
        payload.put("occurredAt", clock.instant().toString());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise outbox payload", e);
        }
    }

    private static void validateAmounts(BigDecimal sale, BigDecimal commission, List<PartInput> parts) {
        if (parts == null || parts.isEmpty()) {
            throw new AmountValidationException("At least one transaction part is required");
        }
        requireNonNegative(sale, "saleAmount");
        requireNonNegative(commission, "commissionAmount");
        if (commission.compareTo(sale) > 0) {
            throw new AmountValidationException("commissionAmount cannot exceed saleAmount");
        }

        BigDecimal partsSale = BigDecimal.ZERO;
        BigDecimal partsCommission = BigDecimal.ZERO;
        for (int i = 0; i < parts.size(); i++) {
            PartInput p = parts.get(i);
            requireNonNegative(p.saleAmount(), "parts[" + i + "].saleAmount");
            requireNonNegative(p.commissionAmount(), "parts[" + i + "].commissionAmount");
            if (p.commissionAmount().compareTo(p.saleAmount()) > 0) {
                throw new AmountValidationException(
                        "parts[" + i + "].commissionAmount cannot exceed parts[" + i + "].saleAmount");
            }
            partsSale = partsSale.add(p.saleAmount());
            partsCommission = partsCommission.add(p.commissionAmount());
        }
        if (partsSale.compareTo(sale) != 0) {
            throw new AmountValidationException(
                    "Sum of part saleAmounts (" + partsSale + ") does not equal transaction saleAmount (" + sale + ")");
        }
        if (partsCommission.compareTo(commission) != 0) {
            throw new AmountValidationException(
                    "Sum of part commissionAmounts (" + partsCommission
                            + ") does not equal transaction commissionAmount (" + commission + ")");
        }
    }

    private static void requireNonNegative(BigDecimal value, String field) {
        if (value == null) {
            throw new AmountValidationException(field + " is required");
        }
        if (value.signum() < 0) {
            throw new AmountValidationException(field + " must be non-negative");
        }
    }
}
