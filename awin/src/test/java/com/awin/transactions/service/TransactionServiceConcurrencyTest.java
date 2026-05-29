package com.awin.transactions.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.awin.transactions.domain.Transaction;
import com.awin.transactions.domain.TransactionStatus;
import com.awin.transactions.outbox.MessagePublisher;
import com.awin.transactions.outbox.OutboxRepository;
import com.awin.transactions.support.RecordingMessagePublisher;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@SpringBootTest(properties = "awin.outbox.poll-interval-ms=3600000")
class TransactionServiceConcurrencyTest {

  @TestConfiguration
  static class TestPublisherConfig {
    @Bean
    @Primary
    MessagePublisher recordingMessagePublisher() {
      return new RecordingMessagePublisher();
    }
  }

  @Autowired TransactionService service;

  @Autowired OutboxRepository outboxRepository;

  @Test
  void onlyOneOfTwoConcurrentApprovesSucceeds() throws Exception {
    Transaction tx =
        service.create(
            new BigDecimal("100.00"),
            new BigDecimal("10.00"),
            List.of(new PartInput(new BigDecimal("100.00"), new BigDecimal("10.00"))));
    UUID id = tx.getId();
    long outboxBefore = outboxRepository.count();

    int threads = 2;
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger successes = new AtomicInteger();
    AtomicInteger conflicts = new AtomicInteger();

    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      for (int i = 0; i < threads; i++) {
        pool.submit(
            () -> {
              ready.countDown();
              try {
                start.await();
                service.approve(id);
                successes.incrementAndGet();
              } catch (ConcurrentTransactionUpdateException
                  | com.awin.transactions.domain.IllegalStateTransitionException e) {
                conflicts.incrementAndGet();
              } catch (Exception e) {
                // Surface as failure
                throw new RuntimeException(e);
              }
              return null;
            });
      }
      ready.await(5, TimeUnit.SECONDS);
      start.countDown();
      pool.shutdown();
      assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    } finally {
      pool.shutdownNow();
    }

    assertThat(successes.get()).isEqualTo(1);
    assertThat(conflicts.get()).isEqualTo(1);

    Transaction reloaded = service.get(id);
    assertThat(reloaded.getStatus()).isEqualTo(TransactionStatus.APPROVED);

    long outboxAfter = outboxRepository.count();
    assertThat(outboxAfter - outboxBefore).isEqualTo(1L);
  }
}
