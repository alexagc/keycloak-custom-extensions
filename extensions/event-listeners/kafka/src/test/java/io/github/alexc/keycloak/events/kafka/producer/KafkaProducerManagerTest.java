package io.github.alexc.keycloak.events.kafka.producer;

import io.github.alexc.keycloak.events.kafka.config.KafkaEventListenerConfig;
import io.github.alexc.keycloak.events.kafka.config.PartitionStrategy;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaProducerManagerTest {

    private KafkaEventListenerConfig createDefaultConfig() {
        return new KafkaEventListenerConfig(
                "localhost:9092",
                "events.{realm}",
                "admin.{realm}",
                PartitionStrategy.USER,
                "1",
                "snappy",
                "PLAINTEXT",
                "PLAIN",
                null,
                500L,
                100,
                Collections.emptySet(),
                true
        );
    }

    @Test
    @DisplayName("Should send record asynchronously to Kafka producer")
    @SuppressWarnings("unchecked")
    void testSendAsyncSuccess() {
        Producer<String, String> producer = mock(Producer.class);
        BoundedRetryRingBuffer buffer = new BoundedRetryRingBuffer(50);
        KafkaProducerManager manager = new KafkaProducerManager(createDefaultConfig(), producer, buffer);

        Future<RecordMetadata> future = mock(Future.class);
        when(producer.send(any(ProducerRecord.class), any(Callback.class))).thenReturn(future);

        manager.sendAsync("topic-a", "user-1", "{\"msg\":\"hello\"}");

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(producer).send(captor.capture(), any(Callback.class));

        ProducerRecord<String, String> record = captor.getValue();
        assertEquals("topic-a", record.topic());
        assertEquals("user-1", record.key());
        assertEquals("{\"msg\":\"hello\"}", record.value());
        assertEquals(0, buffer.size());
    }

    @Test
    @DisplayName("Should offload to retry buffer when immediate exception is thrown")
    @SuppressWarnings("unchecked")
    void testSendAsyncImmediateException() {
        Producer<String, String> producer = mock(Producer.class);
        BoundedRetryRingBuffer buffer = new BoundedRetryRingBuffer(50);
        KafkaProducerManager manager = new KafkaProducerManager(createDefaultConfig(), producer, buffer);

        doThrow(new RuntimeException("Kafka buffer full")).when(producer).send(any(ProducerRecord.class), any(Callback.class));

        manager.sendAsync("topic-a", "user-1", "{\"msg\":\"hello\"}");

        assertEquals(1, buffer.size());
    }

    @Test
    @DisplayName("Should offload to retry buffer when callback returns an exception")
    @SuppressWarnings("unchecked")
    void testSendAsyncCallbackException() {
        Producer<String, String> producer = mock(Producer.class);
        BoundedRetryRingBuffer buffer = new BoundedRetryRingBuffer(50);
        KafkaProducerManager manager = new KafkaProducerManager(createDefaultConfig(), producer, buffer);

        ArgumentCaptor<Callback> callbackCaptor = ArgumentCaptor.forClass(Callback.class);

        manager.sendAsync("topic-a", "user-1", "{\"msg\":\"hello\"}");

        verify(producer).send(any(ProducerRecord.class), callbackCaptor.capture());
        callbackCaptor.getValue().onCompletion(null, new RuntimeException("Broker unreachable"));

        assertEquals(1, buffer.size());
    }

    @Test
    @DisplayName("Should gracefully close producer")
    @SuppressWarnings("unchecked")
    void testClose() {
        Producer<String, String> producer = mock(Producer.class);
        BoundedRetryRingBuffer buffer = new BoundedRetryRingBuffer(50);
        KafkaProducerManager manager = new KafkaProducerManager(createDefaultConfig(), producer, buffer);

        manager.close();
        verify(producer).flush();
        assertNotNull(manager.getRetryBuffer());
    }
}
