package com.springmarket.order;

import com.springmarket.cart.CartRepository;
import com.springmarket.cart.CartService;
import com.springmarket.catalog.CatalogService;
import com.springmarket.catalog.dto.ProductResponse;
import com.springmarket.identity.Role;
import com.springmarket.identity.User;
import com.springmarket.identity.UserRepository;
import com.springmarket.inventory.InventoryService;
import com.springmarket.shared.error.DomainException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrentCheckoutTest {
    private static final UUID BUYER_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String PRODUCT_ID = "same-cart-product";

    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private CartService cartService;
    @Autowired
    private CartRepository cartRepository;
    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private InventoryService inventoryService;
    @MockitoBean
    private CatalogService catalogService;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        userRepository.deleteAll();
        Instant now = Instant.parse("2026-09-26T12:00:00Z");
        userRepository.save(new User(
                BUYER_ID,
                "checkout@example.com",
                "password-hash",
                true,
                now,
                now,
                Set.of(Role.BUYER)));
        when(catalogService.get(PRODUCT_ID)).thenReturn(new ProductResponse(
                PRODUCT_ID,
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "Product",
                "Description",
                new BigDecimal("19.99"),
                "USD",
                "Category",
                true,
                now));
        cartService.addItem(BUYER_ID, PRODUCT_ID, 1);
    }

    @Test
    void concurrentCheckout_sameCart_onlyOneOrderSucceeds() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch reservationsStarted = new CountDownLatch(2);
        doAnswer(invocation -> {
                    reservationsStarted.countDown();
                    reservationsStarted.await(1, TimeUnit.SECONDS);
                    return null;
                })
                .when(inventoryService)
                .reserve(PRODUCT_ID, 1);
        Callable<Throwable> checkout = () -> {
            start.await();
            try {
                orderService.placeOrder(BUYER_ID);
                return null;
            } catch (Throwable exception) {
                return exception;
            }
        };

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(checkout);
            var second = executor.submit(checkout);
            start.countDown();
            var results = Arrays.asList(first.get(), second.get());

            assertEquals(1, results.stream().filter(result -> result == null).count());
            Throwable failure =
                    results.stream().filter(result -> result != null).findFirst().orElseThrow();
            assertInstanceOf(DomainException.class, failure);
            assertEquals("Cart is empty", failure.getMessage());
            assertEquals(1, orderRepository.count());
        }
    }
}
