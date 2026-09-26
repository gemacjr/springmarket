package com.springmarket.inventory;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest
@ActiveProfiles("test")
class InventoryServiceTest {
    @Autowired
    private InventoryService inventoryService;

    @Test
    void concurrentReserve_onlyOneSucceeds() throws Exception {
        String productId = "concurrent-product";
        inventoryService.initialize(productId, 1);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Throwable> reserve = () -> {
            start.await();
            try {
                inventoryService.reserve(productId, 1);
                return null;
            } catch (Throwable exception) {
                return exception;
            }
        };

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(reserve);
            var second = executor.submit(reserve);
            start.countDown();
            List<Throwable> results = java.util.Arrays.asList(first.get(), second.get());

            assertEquals(1, results.stream().filter(result -> result == null).count());
            assertEquals(1, results.stream().filter(result -> result != null).count());
            assertInstanceOf(
                    InsufficientStockException.class,
                    results.stream().filter(result -> result != null).findFirst().orElseThrow());
        }
    }
}
