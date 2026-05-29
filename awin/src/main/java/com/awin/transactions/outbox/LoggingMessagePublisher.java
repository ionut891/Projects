package com.awin.transactions.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default {@link MessagePublisher} that writes a structured log line per event. Stands in for a
 * real broker so the application is self-contained for local runs and tests.
 */
@Component
public class LoggingMessagePublisher implements MessagePublisher {

  private static final Logger log = LoggerFactory.getLogger(LoggingMessagePublisher.class);

  @Override
  public void publish(OutboxEvent event) {
    log.info(
        "Publishing event id={} type={} aggregateId={} payload={}",
        event.getId(),
        event.getType(),
        event.getAggregateId(),
        event.getPayload());
  }
}
