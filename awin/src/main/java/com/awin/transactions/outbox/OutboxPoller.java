package com.awin.transactions.outbox;

import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
