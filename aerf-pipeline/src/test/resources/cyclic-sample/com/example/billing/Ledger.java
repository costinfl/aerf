package com.example.billing;

import com.example.shipping.Crate;

/** Half of a cycle that crosses the billing/shipping boundary. */
public class Ledger {
    private Crate crate;

    public Crate crate() {
        return crate;
    }
}
