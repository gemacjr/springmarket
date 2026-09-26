package com.springmarket.order;

import com.springmarket.order.dto.OrderResponse;
import com.springmarket.shared.security.UserPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse placeOrder(@AuthenticationPrincipal UserPrincipal principal) {
        return orderService.placeOrder(principal.userId());
    }

    @GetMapping("/mine")
    public List<OrderResponse> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return orderService.mine(principal.userId());
    }

    @GetMapping("/{id}")
    public OrderResponse getOwn(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.getOwn(id, principal.userId());
    }
}
