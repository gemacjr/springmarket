package com.springmarket.order;

import com.springmarket.shared.events.PaymentCompletedEvent;
import com.springmarket.shared.events.PaymentFailedEvent;
import com.springmarket.shared.events.ProcessedEventRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.after;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
    "spring.kafka.listener.auto-startup=true",
    "spring.kafka.producer.properties.max.block.ms=10000"
})
@EmbeddedKafka(
        partitions = 1,
        topics = {"payment.completed", "payment.failed"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
@ActiveProfiles("test")
class PaymentResultListenerTest {
    @Autowired
    private PaymentResultListener listener;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @MockitoBean
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        processedEventRepository.deleteAll();
        reset(orderService);
    }

    @Test
    void duplicatePaymentCompleted_throughKafka_doesNotReapply() throws Exception {
        UUID orderId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                Instant.parse("2026-09-26T12:00:00Z"),
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                orderId);

        kafkaTemplate
                .send("payment.completed", orderId.toString(), event)
                .get(10, TimeUnit.SECONDS);
        kafkaTemplate
                .send("payment.completed", orderId.toString(), event)
                .get(10, TimeUnit.SECONDS);

        verify(orderService, after(3_000).times(1)).applyPaymentResult(orderId, true);
    }

    @Test
    void duplicatePaymentFailed_doesNotReapply() {
        UUID orderId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        PaymentFailedEvent event = new PaymentFailedEvent(
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                Instant.parse("2026-09-26T12:01:00Z"),
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                orderId);

        listener.onPaymentFailed(event);
        listener.onPaymentFailed(event);

        verify(orderService, times(1)).applyPaymentResult(orderId, false);
    }
}
