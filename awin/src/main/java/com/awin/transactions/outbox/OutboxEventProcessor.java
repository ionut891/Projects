package com.awin.transactions.outbox;

import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-event step of the outbox drain. Lives in its own bean so each call goes through Spring's
 * transactional proxy and runs in its own short JPA transaction; that way a slow or stuck broker
 * call only holds the DB connection for one event, not for the whole batch.
 */
@Component
public class OutboxEventProcessor {

  private static final Logger log = LoggerFactory.getLogger(OutboxEventProcessor.class);

  private final OutboxRepository outbox;
  private final MessagePublisher publisher;
  private final Clock clock;

  public OutboxEventProcessor(OutboxRepository outbox, MessagePublisher publisher, Clock clock) {
    this.outbox = outbox;
    this.publisher = publisher;
    this.clock = clock;
  }

  /**
   * Re-load the event by id, publish it, and mark it published — or record a failure if the
   * publisher throws. Idempotent: if another process has already published the event in the
   * meantime, this method returns without action.
   */
  @Transactional
  public void process(UUID id) {
    OutboxEvent event = outbox.findById(id).orElse(null);
    if (event == null || event.getPublishedAt() != null) {
      return;
    }
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
