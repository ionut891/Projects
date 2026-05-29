package com.awin.transactions.support;

import com.awin.transactions.outbox.MessagePublisher;
import com.awin.transactions.outbox.OutboxEvent;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class RecordingMessagePublisher implements MessagePublisher {

  private final List<UUID> publishedIds = new CopyOnWriteArrayList<>();
  private volatile RuntimeException failureToThrow;

  @Override
  public void publish(OutboxEvent event) {
    if (failureToThrow != null) {
      throw failureToThrow;
    }
    publishedIds.add(event.getId());
  }

  public List<UUID> publishedIds() {
    return List.copyOf(publishedIds);
  }

  public void failNext(RuntimeException ex) {
    this.failureToThrow = ex;
  }

  public void clearFailure() {
    this.failureToThrow = null;
  }
}
