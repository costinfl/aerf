package com.example.shipping;

import com.example.billing.Ledger;

/** The other half of the cross-subsystem cycle. */
public class Crate {
    private Ledger ledger;

    public Ledger ledger() {
        return ledger;
    }
}
