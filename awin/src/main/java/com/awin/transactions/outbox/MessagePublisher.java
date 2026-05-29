package com.awin.transactions.outbox;

public interface MessagePublisher {

    void publish(OutboxEvent event);
}
