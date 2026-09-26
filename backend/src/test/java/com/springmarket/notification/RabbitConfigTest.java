package com.springmarket.notification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RabbitConfigTest {
    private final RabbitConfig config = new RabbitConfig();

    @Test
    void orderConfirmationQueue_deadLettersFailedMessages() {
        var queue = config.orderConfirmationQueue();

        assertTrue(queue.isDurable());
        assertEquals(
                RabbitConfig.DEAD_LETTER_EXCHANGE,
                queue.getArguments().get("x-dead-letter-exchange"));
        assertEquals(
                RabbitConfig.ORDER_CONFIRMATION_DEAD_LETTER_ROUTING_KEY,
                queue.getArguments().get("x-dead-letter-routing-key"));
    }

    @Test
    void deadLetterQueue_isDurable() {
        assertTrue(config.orderConfirmationDeadLetterQueue().isDurable());
    }
}
