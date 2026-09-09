package com.example;

import java.io.Serializable;

public class Order extends BaseEntity implements Serializable {

    private String status;

    public String getStatus() {
        return status;
    }
}
