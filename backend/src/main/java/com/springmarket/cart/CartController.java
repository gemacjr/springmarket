package com.springmarket.cart;

import com.springmarket.cart.dto.AddCartItemRequest;
import com.springmarket.cart.dto.CartResponse;
import com.springmarket.cart.dto.UpdateCartItemRequest;
import com.springmarket.shared.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {
    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse getCart(@AuthenticationPrincipal UserPrincipal principal) {
        return cartService.getCart(principal.userId());
    }

    @PostMapping("/items")
    public CartResponse addItem(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AddCartItemRequest request) {
        return cartService.addItem(principal.userId(), request.productId(), request.quantity());
    }

    @PutMapping("/items/{productId}")
    public CartResponse updateItem(
            @PathVariable String productId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateItem(principal.userId(), productId, request.quantity());
    }

    @DeleteMapping("/items/{productId}")
    public CartResponse removeItem(
            @PathVariable String productId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return cartService.removeItem(principal.userId(), productId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal UserPrincipal principal) {
        cartService.clear(principal.userId());
    }
}
