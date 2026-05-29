package com.awin.transactions.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.awin.transactions.support.RecordingMessagePublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "awin.outbox.poll-interval-ms=3600000")
@Transactional
class OutboxPollerTest {

    @TestConfiguration
    static class TestPublisherConfig {
        @Bean
        @Primary
        MessagePublisher recordingMessagePublisher() {
            return new RecordingMessagePublisher();
        }
    }

    @Autowired
    OutboxRepository repository;

    @Autowired
    OutboxPoller poller;

    @Autowired
    MessagePublisher publisher;

    @Autowired
    Clock clock;

    private RecordingMessagePublisher recording;

    @BeforeEach
    void setUp() {
        recording = (RecordingMessagePublisher) publisher;
        recording.clearFailure();
    }

    @Test
    void publishesUnpublishedEventsAndMarksThem() {
        OutboxEvent event = repository.save(new OutboxEvent(
                UUID.randomUUID(), "TransactionApproved", "{}", clock.instant()));

        poller.drain();

        assertThat(recording.publishedIds()).contains(event.getId());
        OutboxEvent reloaded = repository.findById(event.getId()).orElseThrow();
        assertThat(reloaded.getPublishedAt()).isNotNull();
        assertThat(reloaded.getAttempts()).isZero();
    }

    @Test
    void leavesEventUnpublishedAndIncrementsAttemptsOnFailure() {
        OutboxEvent event = repository.save(new OutboxEvent(
                UUID.randomUUID(), "TransactionApproved", "{}", Instant.now()));
        recording.failNext(new RuntimeException("broker down"));

        poller.drain();

        OutboxEvent reloaded = repository.findById(event.getId()).orElseThrow();
        assertThat(reloaded.getPublishedAt()).isNull();
        assertThat(reloaded.getAttempts()).isEqualTo(1);
        assertThat(reloaded.getLastError()).contains("broker down");
    }
}
