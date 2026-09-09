package com.example;

public class OrderRepositoryImpl implements OrderRepository {

    @Override
    public Order findById(Long id) {
        return new Order();
    }
}
