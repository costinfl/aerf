package com.example;

import org.springframework.stereotype.Controller;

@Controller
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    public Order handlePlaceOrder(Long id) {
        return orderService.placeOrder(id);
    }
}
