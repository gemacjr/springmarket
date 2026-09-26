package com.springmarket.cart;

import com.springmarket.cart.dto.CartItemResponse;
import com.springmarket.cart.dto.CartResponse;
import com.springmarket.catalog.CatalogService;
import com.springmarket.catalog.dto.ProductResponse;
import com.springmarket.shared.error.DomainException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {
    private final CartRepository cartRepository;
    private final CatalogService catalogService;
    private final Clock clock;

    public CartService(CartRepository cartRepository, CatalogService catalogService, Clock clock) {
        this.cartRepository = cartRepository;
        this.catalogService = catalogService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(UUID userId) {
        return cartRepository
                .findByUserId(userId)
                .map(CartService::toResponse)
                .orElseGet(() -> new CartResponse(null, userId, List.of(), null));
    }

    @Transactional
    public CartResponse addItem(UUID userId, String productId, int quantity) {
        requirePositiveQuantity(quantity);
        ProductResponse product = catalogService.get(productId);
        if (!product.active()) {
            throw new DomainException(HttpStatus.CONFLICT, "Product is not active");
        }
        Cart cart = cartRepository
                .findByUserId(userId)
                .orElseGet(() -> new Cart(userId, clock.instant()));
        cart.addItem(
                productId,
                quantity,
                product.priceAmount(),
                product.currency(),
                clock.instant());
        return toResponse(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse updateItem(UUID userId, String productId, int quantity) {
        requirePositiveQuantity(quantity);
        Cart cart = requireCart(userId);
        if (!cart.updateItem(productId, quantity, clock.instant())) {
            throw new DomainException(HttpStatus.NOT_FOUND, "Cart item not found");
        }
        return toResponse(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse removeItem(UUID userId, String productId) {
        Cart cart = requireCart(userId);
        if (!cart.removeItem(productId, clock.instant())) {
            throw new DomainException(HttpStatus.NOT_FOUND, "Cart item not found");
        }
        return toResponse(cartRepository.save(cart));
    }

    @Transactional
    public void clear(UUID userId) {
        cartRepository.findByUserId(userId).ifPresent(cartRepository::delete);
    }

    private Cart requireCart(UUID userId) {
        return cartRepository
                .findByUserId(userId)
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "Cart not found"));
    }

    private static void requirePositiveQuantity(int quantity) {
        if (quantity <= 0) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "Quantity must be greater than zero");
        }
    }

    private static CartResponse toResponse(Cart cart) {
        return new CartResponse(
                cart.getId(),
                cart.getUserId(),
                cart.getItems().stream()
                        .map(item -> new CartItemResponse(
                                item.getProductId(),
                                item.getQuantity(),
                                item.getUnitPrice(),
                                item.getCurrency()))
                        .toList(),
                cart.getUpdatedAt());
    }
}
