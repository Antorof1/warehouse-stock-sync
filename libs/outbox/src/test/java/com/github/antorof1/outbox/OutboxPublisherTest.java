package com.github.antorof1.outbox;

import com.github.antorof1.outbox.utils.OutboxUtils;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.github.antorof1.outbox.utils.OutboxUtils.PAYLOAD;
import static com.github.antorof1.outbox.utils.OutboxUtils.TOPIC;
import static com.github.antorof1.outbox.utils.OutboxUtils.TYPE;
import static com.github.antorof1.outbox.utils.OutboxUtils.outboxEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {
    private static final int BATCH_SIZE = 25;

    private final OutboxEvent first = outboxEvent(UUID.randomUUID(), "KEY-1");
    private final OutboxEvent second = outboxEvent(UUID.randomUUID(), "KEY-2");
    private final OutboxEvent third = outboxEvent(UUID.randomUUID(), "KEY-3");

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Captor
    private ArgumentCaptor<ProducerRecord<String, String>> recordCaptor;

    private OutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        outboxPublisher = new OutboxPublisher(
            outboxEventRepository, kafkaTemplate, OutboxUtils.properties(BATCH_SIZE, Duration.ofDays(7))
        );
    }

    @Test
    void doesNothingWhenNothingIsPending() {
        given(outboxEventRepository.findPending(BATCH_SIZE)).willReturn(List.of());

        outboxPublisher.poll();

        verifyNoInteractions(kafkaTemplate);
        verify(outboxEventRepository, never()).markPublished(anyList());
    }

    @Test
    void marksEverySentEventPublishedInBatchOrder() {
        givenPending(first, second, third);
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(succeeded());

        outboxPublisher.poll();

        verify(outboxEventRepository).markPublished(List.of(first.getId(), second.getId(), third.getId()));
    }

    @Test
    void sendsTopicKeyPayloadAndTypeHeader() {
        givenPending(first);
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(succeeded());

        outboxPublisher.poll();

        verify(kafkaTemplate).send(recordCaptor.capture());
        ProducerRecord<String, String> record = recordCaptor.getValue();
        assertThat(record.topic()).isEqualTo(TOPIC);
        assertThat(record.key()).isEqualTo("KEY-1");
        assertThat(record.value()).isEqualTo(PAYLOAD);

        Header typeHeader = record.headers().lastHeader("__TypeId__");
        assertThat(typeHeader).isNotNull();
        assertThat(new String(typeHeader.value(), StandardCharsets.UTF_8)).isEqualTo(TYPE);
    }

    @Test
    void leavesEventWhoseDeliveryFailedPendingForTheNextPoll() {
        givenPending(first, second, third);
        given(kafkaTemplate.send(any(ProducerRecord.class)))
            .willReturn(succeeded(), failed(), succeeded());

        outboxPublisher.poll();

        verify(outboxEventRepository).markPublished(List.of(first.getId(), third.getId()));
    }

    @Test
    void stopsTheBatchWhenKafkaIsUnavailable() {
        givenPending(first, second, third);
        given(kafkaTemplate.send(any(ProducerRecord.class)))
            .willReturn(succeeded())
            .willThrow(new KafkaException("metadata", new TimeoutException("broker unreachable")));

        outboxPublisher.poll();

        verify(kafkaTemplate, times(2)).send(any(ProducerRecord.class));
        verify(outboxEventRepository).markPublished(List.of(first.getId()));
    }

    @Test
    void skipsEventThatCanNeverBeSentAndCarriesOn() {
        givenPending(first, second, third);
        given(kafkaTemplate.send(any(ProducerRecord.class)))
            .willReturn(succeeded())
            .willThrow(new KafkaException("too big", new RecordTooLargeException("1 MB")))
            .willReturn(succeeded());

        outboxPublisher.poll();

        verify(kafkaTemplate, times(3)).send(any(ProducerRecord.class));
        verify(outboxEventRepository).markPublished(List.of(first.getId(), third.getId()));
    }

    @Test
    void marksNothingWhenEverySendFails() {
        givenPending(first, second);
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(failed());

        outboxPublisher.poll();

        verify(outboxEventRepository, never()).markPublished(anyList());
    }

    @Test
    void marksNothingWhenKafkaIsUnavailableFromTheStart() {
        givenPending(first, second);
        given(kafkaTemplate.send(any(ProducerRecord.class)))
            .willThrow(new KafkaException("metadata", new TimeoutException("broker unreachable")));

        outboxPublisher.poll();

        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
        verify(outboxEventRepository, never()).markPublished(anyList());
    }

    private void givenPending(OutboxEvent... events) {
        given(outboxEventRepository.findPending(BATCH_SIZE)).willReturn(List.of(events));
    }

    private static CompletableFuture<SendResult<String, String>> succeeded() {
        return CompletableFuture.completedFuture(null);
    }

    private static CompletableFuture<SendResult<String, String>> failed() {
        return CompletableFuture.failedFuture(new KafkaException("delivery timed out"));
    }
}
