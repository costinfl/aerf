package com.example;

import org.springframework.stereotype.Repository;

// The seeded top of the multi-pass role-refinement chain: this interface
// carries the only direct stereotype evidence in the chain
// (OrderRepository -> JpaOrderRepository -> OrderRepositoryImpl below).
@Repository
public interface OrderRepository {

    Order findById(Long id);
}
