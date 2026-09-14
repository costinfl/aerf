package com.example.billing;

/** The other half of billing's own cycle. */
public class LineItem {
    private Invoice invoice;

    public Invoice invoice() {
        return invoice;
    }
}
