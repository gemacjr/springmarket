package com.springmarket.order;

import com.springmarket.cart.CartService;
import com.springmarket.cart.dto.CartItemResponse;
import com.springmarket.cart.dto.CartResponse;
import com.springmarket.inventory.InventoryService;
import com.springmarket.order.dto.OrderItemResponse;
import com.springmarket.order.dto.OrderResponse;
import com.springmarket.payment.PaymentService;
import com.springmarket.payment.PaymentStatus;
import com.springmarket.shared.error.DomainException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final CartService cartService;
    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final OrderStatusMachine orderStatusMachine;
    private final Clock clock;

    public OrderService(
            CartService cartService,
            InventoryService inventoryService,
            OrderRepository orderRepository,
            PaymentService paymentService,
            OrderStatusMachine orderStatusMachine,
            Clock clock) {
        this.cartService = cartService;
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
        this.paymentService = paymentService;
        this.orderStatusMachine = orderStatusMachine;
        this.clock = clock;
    }

    @Transactional
    public OrderResponse placeOrder(UUID buyerId) {
        CartResponse cart = cartService.getCartForCheckout(buyerId);
        if (cart.items().isEmpty()) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "Cart is empty");
        }

        for (CartItemResponse item : cart.items()) {
            inventoryService.reserve(item.productId(), item.quantity());
        }

        Order order = new Order(buyerId, cart.items().getFirst().currency(), clock.instant());
        for (CartItemResponse item : cart.items()) {
            order.addItem(item.productId(), item.quantity(), item.unitPrice(), item.currency());
        }
        Order saved = orderRepository.save(order);
        var payment = paymentService.charge(
                saved.getId(),
                saved.getTotalAmount(),
                saved.getCurrency(),
                saved.getId().toString());
        applyPaymentResult(saved.getId(), payment.status() == PaymentStatus.SUCCESS);
        cartService.clear(buyerId);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> mine(UUID buyerId) {
        return orderRepository.findAllByBuyerIdOrderByCreatedAtDesc(buyerId).stream()
                .map(OrderService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOwn(UUID orderId, UUID buyerId) {
        return toResponse(orderRepository
                .findByIdAndBuyerId(orderId, buyerId)
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "Order not found")));
    }

    @Transactional
    public void applyPaymentResult(UUID orderId, boolean paid) {
        Order order = orderRepository
                .findById(orderId)
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "Order not found"));
        OrderStatus target = paid ? OrderStatus.PAID : OrderStatus.CANCELLED;
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStateException(order.getStatus(), target);
        }
        if (paid) {
            order.transitionTo(target, orderStatusMachine, clock.instant());
            return;
        }

        order.transitionTo(target, orderStatusMachine, clock.instant());
        for (OrderItem item : order.getItems()) {
            inventoryService.release(item.getProductId(), item.getQuantity());
        }
    }

    private static OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getBuyerId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getItems().stream()
                        .map(item -> new OrderItemResponse(
                                item.getProductId(),
                                item.getQuantity(),
                                item.getUnitPrice(),
                                item.getCurrency()))
                        .toList(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
