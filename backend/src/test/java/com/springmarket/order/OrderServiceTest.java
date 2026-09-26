package com.springmarket.order;

import com.springmarket.cart.CartService;
import com.springmarket.cart.dto.CartItemResponse;
import com.springmarket.cart.dto.CartResponse;
import com.springmarket.inventory.InsufficientStockException;
import com.springmarket.inventory.InventoryService;
import com.springmarket.payment.PaymentResult;
import com.springmarket.payment.PaymentService;
import com.springmarket.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

    @Mock
    private CartService cartService;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentService paymentService;

    private final UUID buyerId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final String productId = "product-1";
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                cartService,
                inventoryService,
                orderRepository,
                paymentService,
                new OrderStatusMachine(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void placeOrder_whenInsufficientStock_throws() {
        when(cartService.getCartForCheckout(buyerId)).thenReturn(cart(2));
        doThrow(new InsufficientStockException(productId))
                .when(inventoryService).reserve(productId, 2);

        assertThrows(InsufficientStockException.class, () -> orderService.placeOrder(buyerId));

        verify(orderRepository, never()).save(any());
        verify(cartService, never()).clear(buyerId);
    }

    @Test
    void placeOrder_whenPaymentSucceeds_returnsPaidOrder() {
        AtomicReference<Order> persisted = new AtomicReference<>();
        when(cartService.getCartForCheckout(buyerId)).thenReturn(cart(2));
        when(orderRepository.save(any())).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            persisted.set(order);
            return order;
        });
        when(orderRepository.findById(any()))
                .thenAnswer(invocation -> Optional.ofNullable(persisted.get()));
        when(paymentService.charge(any(), any(), any(), any())).thenAnswer(invocation ->
                new PaymentResult(
                        UUID.randomUUID(), PaymentStatus.SUCCESS, invocation.getArgument(3)));

        var response = orderService.placeOrder(buyerId);

        assertEquals(OrderStatus.PAID, response.status());
        assertEquals(new BigDecimal("39.98"), response.totalAmount());
        assertEquals(NOW, response.createdAt());
        verify(paymentService)
                .charge(response.id(), new BigDecimal("39.98"), "USD", response.id().toString());
        InOrder operations = inOrder(inventoryService, orderRepository, paymentService, cartService);
        operations.verify(inventoryService).reserve(productId, 2);
        operations.verify(orderRepository).save(any());
        operations.verify(paymentService).charge(any(), any(), any(), any());
        operations.verify(cartService).clear(buyerId);
    }

    @Test
    void placeOrder_whenPaymentFails_cancelsAndReleasesInventory() {
        AtomicReference<Order> persisted = new AtomicReference<>();
        when(cartService.getCartForCheckout(buyerId)).thenReturn(cart(2));
        when(orderRepository.save(any())).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            persisted.set(order);
            return order;
        });
        when(orderRepository.findById(any()))
                .thenAnswer(invocation -> Optional.ofNullable(persisted.get()));
        when(paymentService.charge(any(), any(), any(), any())).thenAnswer(invocation ->
                new PaymentResult(
                        UUID.randomUUID(), PaymentStatus.FAILURE, invocation.getArgument(3)));

        var response = orderService.placeOrder(buyerId);

        assertEquals(OrderStatus.CANCELLED, response.status());
        verify(inventoryService).release(productId, 2);
    }

    @Test
    void applyPaymentResult_whenPaid_transitionsPendingToPaid() {
        Order order = pendingOrder();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        orderService.applyPaymentResult(order.getId(), true);

        assertEquals(OrderStatus.PAID, order.getStatus());
        verify(inventoryService, never()).release(any(), anyInt());
    }

    @Test
    void applyPaymentResult_whenFailed_cancelsAndReleasesInventory() {
        Order order = pendingOrder();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        orderService.applyPaymentResult(order.getId(), false);

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        verify(inventoryService).release(productId, 2);
    }

    @Test
    void applyPaymentResult_whenAlreadyPaid_rejectsLateFailureWithoutReleasingInventory() {
        Order order = pendingOrder();
        order.transitionTo(OrderStatus.PAID, new OrderStatusMachine(), NOW);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThrows(
                InvalidOrderStateException.class,
                () -> orderService.applyPaymentResult(order.getId(), false));

        assertEquals(OrderStatus.PAID, order.getStatus());
        verify(inventoryService, never()).release(any(), anyInt());
    }

    @Test
    void getOwn_scopesLookupToBuyer() {
        Order order = pendingOrder();
        when(orderRepository.findByIdAndBuyerId(order.getId(), buyerId)).thenReturn(Optional.of(order));

        var response = orderService.getOwn(order.getId(), buyerId);

        assertEquals(order.getId(), response.id());
        verify(orderRepository).findByIdAndBuyerId(order.getId(), buyerId);
    }

    private CartResponse cart(int quantity) {
        return new CartResponse(
                UUID.randomUUID(),
                buyerId,
                List.of(new CartItemResponse(
                        productId, quantity, new BigDecimal("19.99"), "USD")),
                NOW);
    }

    private Order pendingOrder() {
        Order order = new Order(buyerId, "USD", NOW);
        order.addItem(productId, 2, new BigDecimal("19.99"), "USD");
        return order;
    }
}
