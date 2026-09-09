package com.example;

import org.springframework.stereotype.Repository;

@Repository
public class OrderRepositoryImpl implements OrderRepository {

    @Override
    public Order findById(Long id) {
        return new Order();
    }
}
