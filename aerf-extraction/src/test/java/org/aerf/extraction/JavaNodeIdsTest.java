package org.aerf.extraction;

import org.aerf.model.NodeId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JavaNodeIdsTest {

    @Test
    void moduleIdJoinsGroupAndArtifactWithAColon() {
        assertEquals(NodeId.of("com.example:my-app"), JavaNodeIds.module("com.example", "my-app"));
    }

    @Test
    void typeIdNormalizesBinaryNestedTypeSeparator() {
        assertEquals(NodeId.of("com.example.Outer.Inner"), JavaNodeIds.type("com.example.Outer$Inner"));
    }

    @Test
    void typeIdAcceptsAlreadySourceFormNamesUnchanged() {
        assertEquals(NodeId.of("com.example.Outer.Inner"), JavaNodeIds.type("com.example.Outer.Inner"));
    }

    @Test
    void typeIdNormalizesAnonymousClassBinaryNames() {
        assertEquals(NodeId.of("com.example.Outer.1"), JavaNodeIds.type("com.example.Outer$1"));
    }

    @Test
    void methodIdIncludesOwnerNameAndErasedParameterTypes() {
        NodeId id = JavaNodeIds.method("com.example.OrderService", "find", List.of("java.lang.Long"));

        assertEquals(NodeId.of("com.example.OrderService#find(java.lang.Long)"), id);
    }

    @Test
    void methodIdWithNoParametersHasEmptyParentheses() {
        NodeId id = JavaNodeIds.method("com.example.OrderService", "findAll", List.of());

        assertEquals(NodeId.of("com.example.OrderService#findAll()"), id);
    }

    @Test
    void overloadsWithDifferentParameterTypesProduceDistinctIds() {
        NodeId byLong = JavaNodeIds.method("com.example.OrderService", "find", List.of("java.lang.Long"));
        NodeId byString = JavaNodeIds.method("com.example.OrderService", "find", List.of("java.lang.String"));

        assertNotEquals(byLong, byString);
    }

    @Test
    void methodIdNormalizesTheOwningTypesNestedSeparator() {
        NodeId id = JavaNodeIds.method("com.example.Outer$Inner", "run", List.of());

        assertEquals(NodeId.of("com.example.Outer.Inner#run()"), id);
    }

    @Test
    void blankArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> JavaNodeIds.module("", "app"));
        assertThrows(IllegalArgumentException.class, () -> JavaNodeIds.type("  "));
        assertThrows(IllegalArgumentException.class, () -> JavaNodeIds.method("com.example.Foo", "", List.of()));
    }
}
