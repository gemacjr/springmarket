package com.springmarket.shared.events;

import com.springmarket.order.OrderEventPublisher;
import com.springmarket.payment.PaymentEventPublisher;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class DomainEventPublisherTest {
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void beginTransactionSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @AfterEach
    void clearTransactionSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void orderPlaced_isSentOnlyAfterCommit() {
        UUID orderId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        OrderPlacedEvent event = new OrderPlacedEvent(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                Instant.parse("2026-09-26T12:00:00Z"),
                orderId,
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                new BigDecimal("39.98"),
                "USD");
        OrderEventPublisher publisher = new OrderEventPublisher(kafkaTemplate);

        publisher.publish(event);

        verifyNoInteractions(kafkaTemplate);
        TransactionSynchronizationUtils.triggerAfterCommit();
        verify(kafkaTemplate).send("order.placed", orderId.toString(), event);
    }

    @Test
    void paymentResult_isSentOnlyAfterCommit() {
        UUID orderId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                Instant.parse("2026-09-26T12:01:00Z"),
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                orderId);
        PaymentEventPublisher publisher = new PaymentEventPublisher(kafkaTemplate);

        publisher.publish(event);

        verifyNoInteractions(kafkaTemplate);
        TransactionSynchronizationUtils.triggerAfterCommit();
        verify(kafkaTemplate).send("payment.completed", orderId.toString(), event);
    }

    @Test
    void brokerFailureAfterCommit_doesNotFailCommittedOperation() {
        UUID orderId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        OrderPlacedEvent event = new OrderPlacedEvent(
                UUID.randomUUID(),
                Instant.parse("2026-09-26T12:02:00Z"),
                orderId,
                UUID.randomUUID(),
                new BigDecimal("10.00"),
                "USD");
        when(kafkaTemplate.send("order.placed", orderId.toString(), event))
                .thenThrow(new KafkaException("broker unavailable"));
        new OrderEventPublisher(kafkaTemplate).publish(event);

        assertDoesNotThrow(TransactionSynchronizationUtils::triggerAfterCommit);
    }

    @Test
    void orderPublisher_logsAsyncSendFailure(CapturedOutput output) {
        UUID eventId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        UUID orderId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        OrderPlacedEvent event = new OrderPlacedEvent(
                eventId,
                Instant.parse("2026-09-26T12:03:00Z"),
                orderId,
                UUID.randomUUID(),
                new BigDecimal("10.00"),
                "USD");
        CompletableFuture<SendResult<String, Object>> failedSend = new CompletableFuture<>();
        failedSend.completeExceptionally(new KafkaException("async broker failure"));
        when(kafkaTemplate.send("order.placed", orderId.toString(), event))
                .thenReturn(failedSend);
        new OrderEventPublisher(kafkaTemplate).publish(event);

        TransactionSynchronizationUtils.triggerAfterCommit();

        assertTrue(output.getAll().contains("Could not publish order event " + eventId));
    }

    @Test
    void paymentPublisher_logsAsyncSendFailure(CapturedOutput output) {
        UUID orderId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
                Instant.parse("2026-09-26T12:04:00Z"),
                UUID.randomUUID(),
                orderId);
        CompletableFuture<SendResult<String, Object>> failedSend = new CompletableFuture<>();
        failedSend.completeExceptionally(new KafkaException("async broker failure"));
        when(kafkaTemplate.send("payment.completed", orderId.toString(), event))
                .thenReturn(failedSend);
        new PaymentEventPublisher(kafkaTemplate).publish(event);

        TransactionSynchronizationUtils.triggerAfterCommit();

        assertTrue(output.getAll().contains("Could not publish payment event to payment.completed"));
    }
}
