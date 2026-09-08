package org.aerf.analysis.role.graph;

import org.aerf.analysis.role.GraphRoleRefinementRule;
import org.aerf.analysis.role.RoleInferenceRule;
import org.aerf.model.Graph;
import org.aerf.model.Node;
import org.aerf.model.NodeId;
import org.aerf.model.NodeRef;
import org.aerf.model.NodeType;
import org.aerf.model.RelationType;
import org.aerf.model.Role;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultGraphRefinementRulesTest {

    private final GraphRoleRefinementRule rule = new DefaultGraphRefinementRules.InheritRoleFromSupertype();

    @Test
    void doesNotFireWhenTheSupertypesRoleIsStillUnknown() {
        NodeId sub = NodeId.of("sub");
        NodeId sup = NodeId.of("sup");
        Graph graph = Graph.builder()
                .addNode(Node.withUnknownRole(sub, NodeType.COMPONENT, Map.of(), List.of()))
                .addNode(Node.withUnknownRole(sup, NodeType.COMPONENT, Map.of(), List.of()))
                .addEdge(NodeRef.resolved(sub), NodeRef.resolved(sup), RelationType.EXTENDS, List.of())
                .build();

        List<RoleInferenceRule.Candidate> candidates = rule.refine(sub, graph, Map.of(sub, Role.UNKNOWN, sup, Role.UNKNOWN));

        assertTrue(candidates.isEmpty());
    }

    @Test
    void doesNotFireForRelationsOtherThanExtendsOrImplements() {
        NodeId caller = NodeId.of("caller");
        NodeId callee = NodeId.of("callee");
        Graph graph = Graph.builder()
                .addNode(Node.withUnknownRole(caller, NodeType.COMPONENT, Map.of(), List.of()))
                .addNode(Node.withUnknownRole(callee, NodeType.COMPONENT, Map.of(), List.of()))
                .addEdge(NodeRef.resolved(caller), NodeRef.resolved(callee), RelationType.CALL, List.of())
                .build();

        List<RoleInferenceRule.Candidate> candidates = rule.refine(caller, graph, Map.of(caller, Role.UNKNOWN, callee, Role.PERSISTENCE));

        assertTrue(candidates.isEmpty(), "CALL is not an inheritance relation");
    }

    @Test
    void firesWithTheSupertypesRoleWhenKnown() {
        NodeId sub = NodeId.of("sub");
        NodeId sup = NodeId.of("sup");
        Graph graph = Graph.builder()
                .addNode(Node.withUnknownRole(sub, NodeType.COMPONENT, Map.of(), List.of()))
                .addNode(Node.withUnknownRole(sup, NodeType.COMPONENT, Map.of(), List.of()))
                .addEdge(NodeRef.resolved(sub), NodeRef.resolved(sup), RelationType.IMPLEMENTS, List.of())
                .build();

        List<RoleInferenceRule.Candidate> candidates = rule.refine(sub, graph, Map.of(sub, Role.UNKNOWN, sup, Role.PERSISTENCE));

        assertEquals(1, candidates.size());
        assertEquals(Role.PERSISTENCE, candidates.get(0).role());
    }
}
