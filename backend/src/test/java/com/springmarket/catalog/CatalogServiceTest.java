package com.springmarket.catalog;

import com.springmarket.catalog.dto.CreateProductRequest;
import com.springmarket.catalog.dto.ReviewRequest;
import com.springmarket.catalog.dto.UpdateProductRequest;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private ProductCacheService productCacheService;

    private CatalogService catalogService;

    @BeforeEach
    void setUp() {
        catalogService = new CatalogService(
                productRepository,
                reviewRepository,
                productCacheService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void list_usesRedisOnSecondCall() {
        ProductDocument product = product("product-1");
        AtomicReference<CachedProductPage> cached = new AtomicReference<>();
        when(productCacheService.get("catalog:products:page:0:size:20"))
                .thenAnswer(invocation -> Optional.ofNullable(cached.get()));
        when(productRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                        List.of(product),
                        org.springframework.data.domain.PageRequest.of(0, 20),
                        42));
        org.mockito.Mockito.doAnswer(invocation -> {
                    cached.set(invocation.getArgument(1));
                    return null;
                })
                .when(productCacheService)
                .put(eq("catalog:products:page:0:size:20"), any());

        var first = catalogService.list(0, 20);
        var second = catalogService.list(0, 20);

        verify(productRepository, times(1)).findAll(any(Pageable.class));
        assertEquals(42, first.getTotalElements());
        assertEquals(42, second.getTotalElements());
    }

    @Test
    void create_savesProductAndClearsListCache() {
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = catalogService.create(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                new CreateProductRequest(
                        "Coffee", "Single-origin beans", new BigDecimal("18.50"), "USD", "GROCERY"));

        assertEquals("Coffee", response.name());
        assertEquals(NOW, response.createdAt());
        verify(productCacheService).clear();
    }

    @Test
    void get_missingProductReturnsNotFound() {
        when(productRepository.findById("missing")).thenReturn(Optional.empty());

        var exception = assertThrows(
                com.springmarket.shared.error.DomainException.class,
                () -> catalogService.get("missing"));

        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void addReview_savesBuyerReviewForExistingProduct() {
        when(productRepository.findById("product-1")).thenReturn(Optional.of(product("product-1")));
        when(reviewRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = catalogService.addReview(
                "product-1",
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                new ReviewRequest(5, "Excellent"));

        assertEquals(5, response.rating());
        assertEquals("Excellent", response.comment());
        assertEquals(NOW, response.createdAt());
    }

    @Test
    void update_preservesIdentityAndClearsListCache() {
        ProductDocument existing = product("product-1");
        when(productRepository.findById("product-1")).thenReturn(Optional.of(existing));
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = catalogService.update(
                "product-1",
                existing.sellerId(),
                new UpdateProductRequest(
                        "Tea", "Green tea", new BigDecimal("12.00"), "USD", "GROCERY", true));

        assertEquals("Tea", response.name());
        assertEquals(existing.createdAt(), response.createdAt());
        verify(productCacheService).clear();
    }

    @Test
    void delete_removesProductAndClearsListCache() {
        ProductDocument existing = product("product-1");
        when(productRepository.findById("product-1")).thenReturn(Optional.of(existing));

        catalogService.delete("product-1", existing.sellerId());

        verify(productRepository).delete(existing);
        verify(productCacheService).clear();
    }

    @Test
    void delete_byDifferentSellerIsForbidden() {
        when(productRepository.findById("product-1")).thenReturn(Optional.of(product("product-1")));

        var exception = assertThrows(
                com.springmarket.shared.error.DomainException.class,
                () -> catalogService.delete(
                        "product-1",
                        UUID.fromString("33333333-3333-3333-3333-333333333333")));

        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, exception.getStatus());
    }

    private ProductDocument product(String id) {
        return new ProductDocument(
                id,
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "Coffee",
                "Single-origin beans",
                new BigDecimal("18.50"),
                "USD",
                "GROCERY",
                true,
                NOW);
    }
}
