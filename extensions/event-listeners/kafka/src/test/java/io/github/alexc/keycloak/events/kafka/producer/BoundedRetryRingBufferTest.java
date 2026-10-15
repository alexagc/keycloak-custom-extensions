package io.github.alexc.keycloak.events.kafka.producer;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BoundedRetryRingBufferTest {

    @Test
    @DisplayName("Should enqueue records and track size properly")
    void testEnqueue() {
        BoundedRetryRingBuffer buffer = new BoundedRetryRingBuffer(10);
        assertTrue(buffer.enqueue("topic1", "key1", "payload1"));
        assertTrue(buffer.enqueue("topic2", "key2", "payload2"));
        assertEquals(2, buffer.size());
    }

    @Test
    @DisplayName("Should ignore null topic or payload on enqueue")
    void testEnqueueNull() {
        BoundedRetryRingBuffer buffer = new BoundedRetryRingBuffer(5);
        assertFalse(buffer.enqueue(null, "k", "p"));
        assertFalse(buffer.enqueue("t", "k", null));
        assertEquals(0, buffer.size());
    }

    @Test
    @DisplayName("Should drop oldest record (FIFO) when capacity is reached")
    void testCapacityDropOldest() {
        BoundedRetryRingBuffer buffer = new BoundedRetryRingBuffer(3);
        buffer.enqueue("t", "k1", "first");
        buffer.enqueue("t", "k2", "second");
        buffer.enqueue("t", "k3", "third");
        assertEquals(3, buffer.size());
        assertEquals(0, buffer.getDroppedCount());

        // 4th insert should drop the oldest ('first')
        buffer.enqueue("t", "k4", "fourth");
        assertEquals(3, buffer.size());
        assertEquals(1, buffer.getDroppedCount());
    }

    @Test
    @DisplayName("Should drain records successfully to producer")
    @SuppressWarnings("unchecked")
    void testDrainSuccessful() {
        BoundedRetryRingBuffer buffer = new BoundedRetryRingBuffer(5);
        buffer.enqueue("topic", "key1", "payload1");
        buffer.enqueue("topic", "key2", "payload2");

        Producer<String, String> producer = mock(Producer.class);
        Future<RecordMetadata> future = mock(Future.class);
        when(producer.send(any(ProducerRecord.class), any(Callback.class))).thenReturn(future);

        int drained = buffer.drain(producer, 10);
        assertEquals(2, drained);
        assertEquals(0, buffer.size());

        verify(producer, times(2)).send(any(ProducerRecord.class), any(Callback.class));
    }

    @Test
    @DisplayName("Should re-enqueue record if producer callback returns an exception")
    @SuppressWarnings("unchecked")
    void testDrainCallbackExceptionReEnqueue() {
        BoundedRetryRingBuffer buffer = new BoundedRetryRingBuffer(5);
        buffer.enqueue("topic", "key1", "payload1");

        Producer<String, String> producer = mock(Producer.class);
        ArgumentCaptor<Callback> callbackCaptor = ArgumentCaptor.forClass(Callback.class);

        buffer.drain(producer, 1);
        verify(producer).send(any(ProducerRecord.class), callbackCaptor.capture());

        // Simulate callback error
        callbackCaptor.getValue().onCompletion(null, new RuntimeException("Broker disconnected"));

        // Verify the record was re-enqueued
        assertEquals(1, buffer.size());
    }

    @Test
    @DisplayName("Should throw exception if capacity is not positive")
    void testInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new BoundedRetryRingBuffer(0));
        assertThrows(IllegalArgumentException.class, () -> new BoundedRetryRingBuffer(-5));
    }

    @Test
    @DisplayName("Should clear buffer")
    void testClear() {
        BoundedRetryRingBuffer buffer = new BoundedRetryRingBuffer(5);
        buffer.enqueue("topic", "key", "payload");
        assertEquals(1, buffer.size());
        buffer.clear();
        assertEquals(0, buffer.size());
    }
}
