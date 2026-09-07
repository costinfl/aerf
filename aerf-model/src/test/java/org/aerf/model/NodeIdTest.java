package org.aerf.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NodeIdTest {

    @Test
    void equalValuesAreEqual() {
        assertEquals(NodeId.of("com.example.OrderService"), NodeId.of("com.example.OrderService"));
    }

    @Test
    void differentValuesAreNotEqual() {
        assertNotEquals(NodeId.of("a"), NodeId.of("b"));
    }

    @Test
    void blankValueIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> NodeId.of("   "));
    }
}
