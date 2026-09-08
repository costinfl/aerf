package org.aerf.analysis.invariant;

import org.aerf.model.Graph;
import org.aerf.model.Node;
import org.aerf.model.NodeId;
import org.aerf.model.NodeType;
import org.aerf.model.Role;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.aerf.analysis.invariant.InvariantDsl.eq;
import static org.aerf.analysis.invariant.InvariantDsl.nodeType;
import static org.aerf.analysis.invariant.InvariantDsl.not;
import static org.aerf.analysis.invariant.InvariantDsl.nodeRole;
import static org.aerf.analysis.invariant.InvariantDsl.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvariantEvaluatorNodeScopeTest {

    private final InvariantEvaluator evaluator = new InvariantEvaluator();

    // when: node.type == DATA
    // assert: not(node.role == Unknown)
    private final Invariant dataNodesMustHaveAKnownRole = new Invariant(
            "data_nodes_must_have_a_known_role",
            Scope.NODE,
            eq(nodeType(), value(NodeType.DATA)),
            not(eq(nodeRole(), value(Role.UNKNOWN))),
            "high");

    private static Node node(String id, NodeType type, Role role) {
        return Node.of(NodeId.of(id), type, role, Map.of(), List.of());
    }

    @Test
    void aDataNodeWithAKnownRolePasses() {
        Graph graph = Graph.builder().addNode(node("order", NodeType.DATA, Role.DOMAIN)).build();

        InvariantEvaluationResult result = evaluator.evaluate(dataNodesMustHaveAKnownRole, graph, Map.of());

        assertTrue(result.holds());
    }

    @Test
    void aDataNodeWithUnknownRoleViolates() {
        Graph graph = Graph.builder().addNode(node("legacyBlob", NodeType.DATA, Role.UNKNOWN)).build();

        InvariantEvaluationResult result = evaluator.evaluate(dataNodesMustHaveAKnownRole, graph, Map.of());

        assertEquals(1, result.violations().size());
        assertEquals(new ViolationSubject.OfNode(NodeId.of("legacyBlob")), result.violations().get(0).subject());
    }

    @Test
    void aNonDataNodeWithUnknownRoleIsExcludedByTheWhenFilter() {
        Graph graph = Graph.builder().addNode(node("someComponent", NodeType.COMPONENT, Role.UNKNOWN)).build();

        InvariantEvaluationResult result = evaluator.evaluate(dataNodesMustHaveAKnownRole, graph, Map.of());

        assertTrue(result.holds(), "the invariant only applies to DATA nodes");
    }

    @Test
    void eachViolatingNodeIsReportedSeparately() {
        Graph graph = Graph.builder()
                .addNode(node("a", NodeType.DATA, Role.UNKNOWN))
                .addNode(node("b", NodeType.DATA, Role.UNKNOWN))
                .addNode(node("c", NodeType.DATA, Role.DOMAIN))
                .build();

        InvariantEvaluationResult result = evaluator.evaluate(dataNodesMustHaveAKnownRole, graph, Map.of());

        assertEquals(2, result.violations().size());
    }
}
