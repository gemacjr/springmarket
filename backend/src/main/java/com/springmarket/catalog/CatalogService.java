package com.springmarket.catalog;

import com.springmarket.catalog.dto.CreateProductRequest;
import com.springmarket.catalog.dto.ProductResponse;
import com.springmarket.catalog.dto.ReviewRequest;
import com.springmarket.catalog.dto.ReviewResponse;
import com.springmarket.catalog.dto.UpdateProductRequest;
import com.springmarket.shared.error.DomainException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {
    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final ProductCacheService productCacheService;
    private final Clock clock;

    public CatalogService(
            ProductRepository productRepository,
            ReviewRepository reviewRepository,
            ProductCacheService productCacheService,
            Clock clock) {
        this.productRepository = productRepository;
        this.reviewRepository = reviewRepository;
        this.productCacheService = productCacheService;
        this.clock = clock;
    }

    public ProductResponse create(UUID sellerId, CreateProductRequest request) {
        ProductDocument product = new ProductDocument(
                UUID.randomUUID().toString(),
                sellerId,
                request.name(),
                request.description(),
                request.priceAmount(),
                request.currency(),
                request.category(),
                true,
                clock.instant());
        ProductDocument saved = productRepository.save(product);
        productCacheService.clear();
        return toResponse(saved);
    }

    public Page<ProductResponse> list(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "Page must be non-negative and size must be 1-100");
        }
        String key = "catalog:products:page:%d:size:%d".formatted(page, size);
        CachedProductPage cachedPage = productCacheService.get(key).orElseGet(() -> {
            Page<ProductDocument> loaded = productRepository.findAll(PageRequest.of(page, size));
            CachedProductPage entry =
                    new CachedProductPage(loaded.getContent(), loaded.getTotalElements());
            productCacheService.put(key, entry);
            return entry;
        });
        return new PageImpl<>(
                cachedPage.content().stream().map(CatalogService::toResponse).toList(),
                PageRequest.of(page, size),
                cachedPage.totalElements());
    }

    public ProductResponse get(String productId) {
        return toResponse(findProduct(productId));
    }

    public ProductResponse update(String productId, UUID sellerId, UpdateProductRequest request) {
        ProductDocument current = ownedProduct(productId, sellerId);
        ProductDocument updated = new ProductDocument(
                current.id(),
                current.sellerId(),
                request.name(),
                request.description(),
                request.priceAmount(),
                request.currency(),
                request.category(),
                request.active(),
                current.createdAt());
        ProductDocument saved = productRepository.save(updated);
        productCacheService.clear();
        return toResponse(saved);
    }

    public void delete(String productId, UUID sellerId) {
        ProductDocument product = ownedProduct(productId, sellerId);
        productRepository.delete(product);
        productCacheService.clear();
    }

    public ReviewResponse addReview(String productId, UUID buyerId, ReviewRequest request) {
        findProduct(productId);
        ReviewDocument review = new ReviewDocument(
                UUID.randomUUID().toString(),
                productId,
                buyerId,
                request.rating(),
                request.comment(),
                clock.instant());
        ReviewDocument saved = reviewRepository.save(review);
        return new ReviewResponse(
                saved.id(),
                saved.productId(),
                saved.buyerId(),
                saved.rating(),
                saved.comment(),
                saved.createdAt());
    }

    private ProductDocument findProduct(String productId) {
        return productRepository
                .findById(productId)
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    private ProductDocument ownedProduct(String productId, UUID sellerId) {
        ProductDocument product = findProduct(productId);
        if (!product.sellerId().equals(sellerId)) {
            throw new DomainException(HttpStatus.FORBIDDEN, "Only the product seller may modify it");
        }
        return product;
    }

    private static ProductResponse toResponse(ProductDocument product) {
        return new ProductResponse(
                product.id(),
                product.sellerId(),
                product.name(),
                product.description(),
                product.priceAmount(),
                product.currency(),
                product.category(),
                product.active(),
                product.createdAt());
    }
}
