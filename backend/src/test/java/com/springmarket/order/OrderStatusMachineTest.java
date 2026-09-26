package com.springmarket.order;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderStatusMachineTest {
    private final OrderStatusMachine orderStatusMachine = new OrderStatusMachine();

    @Test
    void illegalTransition_deliveredToPending_rejected() {
        assertThrows(InvalidOrderStateException.class,
                () -> orderStatusMachine.assertCanTransition(OrderStatus.DELIVERED, OrderStatus.PENDING));
    }
}
