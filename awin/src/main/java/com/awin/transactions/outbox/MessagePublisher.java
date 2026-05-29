package com.awin.transactions.outbox;

/**
 * Strategy for shipping an {@link OutboxEvent} off-process. The default implementation logs the
 * event; a production deployment would substitute a Kafka / SQS / RabbitMQ adapter by registering a
 * different bean of this type.
 *
 * <p>The contract is at-least-once: implementations should not assume an event is delivered only
 * once, and downstream consumers must dedupe on {@link OutboxEvent#getId()}. Implementations should
 * throw a {@code RuntimeException} on a transient failure so the poller increments
 * {@link OutboxEvent#getAttempts() attempts} and retries on the next tick.
 */
public interface MessagePublisher {

    void publish(OutboxEvent event);
}
