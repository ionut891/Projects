package com.awin.transactions.outbox;

import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodically drains unpublished {@link OutboxEvent}s and hands each one to the configured
 * {@link MessagePublisher}.
 *
 * <p>Runs on a fixed delay (default {@code 1s}, override with {@code awin.outbox.poll-interval-ms})
 * inside its own transaction so updates to {@link OutboxEvent#markPublished(java.time.Instant)} are
 * isolated from the domain transaction that produced the event. Failures of the publisher are
 * caught per-event so a single poisoned message cannot block the rest of the batch.
 */
@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    private static final int BATCH_SIZE = 50;

    private final OutboxRepository outbox;
    private final MessagePublisher publisher;
    private final Clock clock;

    public OutboxPoller(OutboxRepository outbox, MessagePublisher publisher, Clock clock) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.clock = clock;
    }

    /**
     * Read one batch of unpublished events in {@code createdAt} order and attempt to publish each.
     * Invoked by the Spring scheduler; also called directly from tests.
     */
    @Scheduled(fixedDelayString = "${awin.outbox.poll-interval-ms:1000}")
    @Transactional
    public void drain() {
        List<OutboxEvent> batch = outbox.findUnpublished(PageRequest.of(0, BATCH_SIZE));
        for (OutboxEvent event : batch) {
            try {
                publisher.publish(event);
                event.markPublished(clock.instant());
            } catch (RuntimeException e) {
                log.warn(
                        "Failed to publish outbox event id={} type={} attempts={}: {}",
                        event.getId(),
                        event.getType(),
                        event.getAttempts() + 1,
                        e.getMessage());
                event.recordFailure(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    }
}
