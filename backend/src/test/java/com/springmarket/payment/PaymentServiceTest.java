package com.springmarket.payment;

import com.springmarket.shared.events.PaymentCompletedEvent;
import com.springmarket.shared.events.PaymentFailedEvent;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DataJpaTest
@Import(PaymentService.class)
@ActiveProfiles("test")
class PaymentServiceTest {
    private final UUID orderId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final BigDecimal amount = new BigDecimal("39.98");

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private PaymentEventPublisher paymentEventPublisher;

    @Test
    void charge_sameIdempotencyKey_returnsOriginalAndDoesNotDoubleInsert() {
        PaymentResult a = paymentService.charge(orderId, amount, "USD", "key-1");
        PaymentResult b = paymentService.charge(orderId, amount, "USD", "key-1");
        assertEquals(a.paymentId(), b.paymentId());
        assertEquals(1, paymentRepository.count());
        verify(paymentEventPublisher, times(1)).publish(any(PaymentCompletedEvent.class));
    }

    @Test
    void charge_keyEndingWithFail_returnsFailure() {
        PaymentResult result =
                paymentService.charge(orderId, amount, "USD", "decline-FAIL");

        assertEquals(PaymentStatus.FAILURE, result.status());
        ArgumentCaptor<PaymentFailedEvent> eventCaptor =
                ArgumentCaptor.forClass(PaymentFailedEvent.class);
        verify(paymentEventPublisher).publish(eventCaptor.capture());
        PaymentFailedEvent event = eventCaptor.getValue();
        assertEquals(result.paymentId(), event.eventId());
        assertEquals(result.paymentId(), event.paymentId());
        assertEquals(orderId, event.orderId());
    }

    @Test
    void charge_success_publishesCompletedEvent() {
        PaymentResult result = paymentService.charge(orderId, amount, "USD", "success-key");

        ArgumentCaptor<PaymentCompletedEvent> eventCaptor =
                ArgumentCaptor.forClass(PaymentCompletedEvent.class);
        verify(paymentEventPublisher).publish(eventCaptor.capture());
        PaymentCompletedEvent event = eventCaptor.getValue();
        assertEquals(result.paymentId(), event.eventId());
        assertEquals(result.paymentId(), event.paymentId());
        assertEquals(orderId, event.orderId());
    }
}
