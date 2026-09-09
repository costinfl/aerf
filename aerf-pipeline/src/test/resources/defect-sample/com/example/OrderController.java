package com.example;

import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.List;

// Deliberately skips an Application-layer service and depends on
// OrderRepository directly - the planted Presentation->Persistence
// layering violation the ExtractionAdapter Plan's Verification section
// asks Increment 16 to confirm the pipeline actually finds, matching
// AERF v0.4 section 6.3's own worked invariant example
// (no_presentation_to_persistence) exactly.
@Controller
public class OrderController {

    private final OrderRepository repository;

    public OrderController(OrderRepository repository) {
        this.repository = repository;
    }

    /** A single direct call to Persistence - the layering violation itself. */
    public Order getOrder(Long id) {
        return repository.findById(id);
    }

    /**
     * The same direct dependency, called repeatedly inside an explicit
     * for-each loop (not a stream pipeline - {@code JavaSourceExtractor}'s
     * loop-nesting detection only tracks the four syntactic loop forms,
     * not stream/lambda call chains, see Increment 14) - the planted N+1
     * fixture (AERF v0.4 section 4.3), sharing the same layering-violating
     * call target deliberately: a real N+1 defect is very often also a
     * layering shortcut, not two unrelated code smells.
     */
    public List<Order> getManyOrders(List<Long> ids) {
        List<Order> orders = new ArrayList<>();
        for (Long id : ids) {
            orders.add(repository.findById(id));
        }
        return orders;
    }
}
