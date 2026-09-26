package com.springmarket.order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findAllByBuyerIdOrderByCreatedAtDesc(UUID buyerId);

    Optional<Order> findByIdAndBuyerId(UUID id, UUID buyerId);
}
