package com.springmarket.catalog;

import java.util.Optional;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.stereotype.Service;

@Service
@EnableCaching
public class ProductCacheService {
    private static final String CACHE_NAME = "productList";

    private final CacheManager cacheManager;

    public ProductCacheService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public Optional<CachedProductPage> get(String key) {
        Cache.ValueWrapper cached = cache().get(key);
        if (cached == null) {
            return Optional.empty();
        }
        CachedProductPage page = (CachedProductPage) cached.get();
        return Optional.ofNullable(page);
    }

    public void put(String key, CachedProductPage page) {
        cache().put(key, page);
    }

    public void clear() {
        cache().clear();
    }

    private Cache cache() {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) {
            throw new IllegalStateException("Cache not configured: " + CACHE_NAME);
        }
        return cache;
    }
}
