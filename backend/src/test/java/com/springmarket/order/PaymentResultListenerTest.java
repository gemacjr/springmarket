package com.springmarket.order;

import com.springmarket.shared.events.PaymentCompletedEvent;
import com.springmarket.shared.events.PaymentFailedEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DataJpaTest
@Import(PaymentResultListener.class)
@ActiveProfiles("test")
class PaymentResultListenerTest {
    @Autowired
    private PaymentResultListener listener;

    @MockitoBean
    private OrderService orderService;

    @Test
    void duplicatePaymentCompleted_doesNotReapply() {
        UUID orderId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                Instant.parse("2026-09-26T12:00:00Z"),
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                orderId);

        listener.onPaymentCompleted(event);
        listener.onPaymentCompleted(event);

        verify(orderService, times(1)).applyPaymentResult(orderId, true);
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
