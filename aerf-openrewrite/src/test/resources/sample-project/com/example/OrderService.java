package com.example;

public class OrderService {

    private final OrderRepository repository;
    private final LegacyWidget widget;

    public OrderService(OrderRepository repository, LegacyWidget widget) {
        this.repository = repository;
        this.widget = widget;
    }

    public Order placeOrder(Long id) {
        return repository.findById(id);
    }

    // Deliberately calls the same repository method again, but this time
    // inside a for-each loop - the ITERATED execution context fixture for
    // the N+1 heuristic (AERF v0.4 section 4.3).
    public void reprocessAll(java.util.List<Long> ids) {
        for (Long id : ids) {
            repository.findById(id);
        }
    }

    // legacyOperation() is not declared anywhere reachable (LegacyWidget's
    // own supertype is deliberately unresolved) - this call's method type
    // must not resolve, the L1_SYNTAX CALL fixture.
    public void touchLegacyWidget() {
        widget.legacyOperation();
    }
}
