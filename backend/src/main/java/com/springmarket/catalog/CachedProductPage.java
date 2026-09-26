package com.springmarket.catalog;

import java.io.Serializable;
import java.util.List;

public record CachedProductPage(List<ProductDocument> content, long totalElements)
        implements Serializable {
    public CachedProductPage {
        content = List.copyOf(content);
    }
}
