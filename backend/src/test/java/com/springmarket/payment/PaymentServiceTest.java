package com.springmarket.payment;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @Test
    void charge_sameIdempotencyKey_returnsOriginalAndDoesNotDoubleInsert() {
        PaymentResult a = paymentService.charge(orderId, amount, "USD", "key-1");
        PaymentResult b = paymentService.charge(orderId, amount, "USD", "key-1");
        assertEquals(a.paymentId(), b.paymentId());
        assertEquals(1, paymentRepository.count());
    }

    @Test
    void charge_keyEndingWithFail_returnsFailure() {
        PaymentResult result =
                paymentService.charge(orderId, amount, "USD", "decline-FAIL");

        assertEquals(PaymentStatus.FAILURE, result.status());
    }
}
