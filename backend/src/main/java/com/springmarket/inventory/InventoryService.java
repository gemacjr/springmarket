package com.springmarket.inventory;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.springmarket.shared.error.DomainException;

@Service
public class InventoryService {
    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional
    public void initialize(String productId, int quantity) {
        if (quantity < 0) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "Initial stock must not be negative");
        }
        inventoryRepository.save(new InventoryItem(productId, quantity));
    }

    @Transactional
    public void reserve(String productId, int quantity) {
        InventoryItem item = inventoryRepository
                .findById(productId)
                .orElseThrow(() -> new InsufficientStockException(productId));
        item.reserve(quantity);
        try {
            inventoryRepository.saveAndFlush(item);
        } catch (OptimisticLockingFailureException exception) {
            throw new InsufficientStockException(productId);
        }
    }

    @Transactional
    public void release(String productId, int quantity) {
        InventoryItem item = inventoryRepository
                .findById(productId)
                .orElseThrow(() -> new InsufficientStockException(productId));
        item.release(quantity);
        inventoryRepository.saveAndFlush(item);
    }
}
