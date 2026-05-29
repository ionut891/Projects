package com.awin.transactions.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically drains unpublished {@link OutboxEvent}s and hands each one to
 * {@link OutboxEventProcessor}.
 *
 * <p>Runs on a fixed delay (default {@code 1s}, override with {@code awin.outbox.poll-interval-ms}).
 * The poller itself is intentionally non-transactional: it only reads a batch of ids and dispatches
 * each one to the processor, so the JPA transaction lives only for the per-event publish step and
 * is never held open across a slow or stuck broker call.
 */
@Component
public class OutboxPoller {

    private static final int BATCH_SIZE = 50;

    private final OutboxRepository outbox;
    private final OutboxEventProcessor processor;

    public OutboxPoller(OutboxRepository outbox, OutboxEventProcessor processor) {
        this.outbox = outbox;
        this.processor = processor;
    }

    /**
     * Read one batch of unpublished event ids in {@code createdAt} order and dispatch each to the
     * processor. Invoked by the Spring scheduler; also called directly from tests.
     */
    @Scheduled(fixedDelayString = "${awin.outbox.poll-interval-ms:1000}")
    public void drain() {
        List<UUID> ids = outbox.findUnpublishedIds(PageRequest.of(0, BATCH_SIZE));
        for (UUID id : ids) {
            processor.process(id);
        }
    }
}
