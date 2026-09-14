package com.example.billing;

/** Half of an intra-subsystem cycle: Invoice and LineItem refer to each other. */
public class Invoice {
    private LineItem primaryLine;

    public LineItem primaryLine() {
        return primaryLine;
    }
}
