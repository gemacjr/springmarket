package com.springmarket.cart;

import com.springmarket.cart.dto.CartResponse;
import com.springmarket.catalog.CatalogService;
import com.springmarket.catalog.dto.ProductResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CatalogService catalogService;

    private final UUID userId = UUID.randomUUID();
    private final String productId = UUID.randomUUID().toString();
    private CartService cartService;

    @BeforeEach
    void setUp() {
        lenient().when(cartRepository.findByUserId(userId)).thenReturn(Optional.empty());
        lenient()
                .when(cartRepository.save(any(Cart.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        cartService = new CartService(
                cartRepository,
                catalogService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void addItem_snapshotsUnitPrice() {
        when(catalogService.get(productId)).thenReturn(productWithPrice("19.99"));
        CartResponse cart = cartService.addItem(userId, productId, 2);
        assertEquals(new BigDecimal("19.99"), cart.items().getFirst().unitPrice());
        assertEquals(2, cart.items().getFirst().quantity());
    }

    @Test
    void getCart_missingCartReturnsStableEmptyShape() {
        CartResponse first = cartService.getCart(userId);
        CartResponse second = cartService.getCart(userId);

        assertEquals(new CartResponse(null, userId, java.util.List.of(), null), first);
        assertEquals(first, second);
    }

    @Test
    void getCartForCheckout_usesLockedRepositoryLookup() {
        Cart existing = cartWithItem(2, "19.99");
        when(cartRepository.findByUserIdForCheckout(userId)).thenReturn(Optional.of(existing));

        CartResponse cart = cartService.getCartForCheckout(userId);

        assertEquals(1, cart.items().size());
        verify(cartRepository).findByUserIdForCheckout(userId);
    }

    @Test
    void addItem_existingProductIncrementsQuantityAndRefreshesSnapshot() {
        Cart existing = cartWithItem(2, "10.00");
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(catalogService.get(productId)).thenReturn(productWithPrice("19.99"));

        CartResponse cart = cartService.addItem(userId, productId, 3);

        assertEquals(5, cart.items().getFirst().quantity());
        assertEquals(new BigDecimal("19.99"), cart.items().getFirst().unitPrice());
    }

    @Test
    void updateItem_changesQuantityWithoutCallingCatalog() {
        Cart existing = cartWithItem(2, "19.99");
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

        CartResponse cart = cartService.updateItem(userId, productId, 4);

        assertEquals(4, cart.items().getFirst().quantity());
    }

    @Test
    void removeItem_removesMatchingProduct() {
        Cart existing = cartWithItem(2, "19.99");
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

        CartResponse cart = cartService.removeItem(userId, productId);

        assertEquals(0, cart.items().size());
    }

    @Test
    void clear_deletesUsersCart() {
        Cart existing = cartWithItem(2, "19.99");
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

        cartService.clear(userId);

        verify(cartRepository).delete(existing);
    }

    private Cart cartWithItem(int quantity, String price) {
        Cart cart = new Cart(userId, NOW);
        cart.addItem(productId, quantity, new BigDecimal(price), "USD", NOW);
        return cart;
    }

    private ProductResponse productWithPrice(String price) {
        return new ProductResponse(
                productId,
                UUID.randomUUID(),
                "Product",
                "Description",
                new BigDecimal(price),
                "USD",
                "Category",
                true,
                NOW);
    }
}
