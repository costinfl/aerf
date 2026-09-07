package org.aerf.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NodeRefTest {

    @Test
    void resolvedWrapsANodeId() {
        NodeId id = NodeId.of("com.example.OrderService");

        NodeRef ref = NodeRef.resolved(id);

        assertEquals(new NodeRef.Resolved(id), ref);
    }

    @Test
    void unresolvedCarriesADescriptionWithoutAnyNodeId() {
        NodeRef ref = NodeRef.unresolved("dynamic dispatch target could not be resolved statically");

        String description = switch (ref) {
            case NodeRef.Resolved r -> throw new AssertionError("expected Unresolved");
            case NodeRef.Unresolved u -> u.description();
        };

        assertEquals("dynamic dispatch target could not be resolved statically", description);
    }

    @Test
    void blankUnresolvedDescriptionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> NodeRef.unresolved(""));
    }
}
