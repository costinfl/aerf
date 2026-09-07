package org.aerf.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NodeTest {

    @Test
    void identityIsDeterminedByIdAloneNotByRoleOrAttributes() {
        NodeId id = NodeId.of("com.example.OrderService");
        Node asPresentation = Node.of(id, NodeType.COMPONENT, Role.PRESENTATION, Map.of(), List.of());
        Node asDomain = Node.of(id, NodeType.COMPONENT, Role.DOMAIN, Map.of("stereotype", "service"), List.of());

        assertEquals(asPresentation, asDomain, "nodes sharing an id are the same graph entity regardless of role");
        assertEquals(asPresentation.hashCode(), asDomain.hashCode());
    }

    @Test
    void unknownRoleIsAFirstClassOutcomeNotAnError() {
        Node node = Node.withUnknownRole(NodeId.of("legacy.GeneratedStub"), NodeType.COMPONENT, Map.of(), List.of());

        assertEquals(Role.UNKNOWN, node.role());
    }

    @Test
    void withRoleProducesAnUpdatedCopyWithoutMutatingTheOriginal() {
        Node original = Node.withUnknownRole(NodeId.of("com.example.OrderController"), NodeType.COMPONENT, Map.of(), List.of());

        Node refined = original.withRole(Role.PRESENTATION);

        assertEquals(Role.UNKNOWN, original.role());
        assertEquals(Role.PRESENTATION, refined.role());
        assertEquals(original, refined, "role inference must not change node identity");
    }

    @Test
    void attributesAreImmutable() {
        Node node = Node.of(NodeId.of("id"), NodeType.COMPONENT, Role.UNKNOWN, Map.of("k", "v"), List.of());

        assertThrows(UnsupportedOperationException.class, () -> node.attributes().put("x", "y"));
    }

    @Test
    void evidenceListIsImmutable() {
        Evidence evidence = Evidence.of("java", "class declaration observed", ExtractionFidelity.L1_SYNTAX);
        Node node = Node.of(NodeId.of("id"), NodeType.COMPONENT, Role.UNKNOWN, Map.of(), List.of(evidence));

        assertThrows(UnsupportedOperationException.class, () -> node.evidence().add(evidence));
    }
}
