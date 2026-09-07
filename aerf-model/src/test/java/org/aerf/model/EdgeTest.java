package org.aerf.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EdgeTest {

    @Test
    void edgeWithResolvedEndpointsCarriesRelationAndProvenance() {
        Evidence evidence = Evidence.of("java", "method call observed", ExtractionFidelity.L2_SYMBOL_RESOLVED);
        NodeRef source = NodeRef.resolved(NodeId.of("com.example.OrderController"));
        NodeRef target = NodeRef.resolved(NodeId.of("com.example.OrderService"));

        Edge edge = Edge.of(source, target, RelationType.CALL, List.of(evidence));

        assertEquals(source, edge.source());
        assertEquals(target, edge.target());
        assertEquals(RelationType.CALL, edge.relation());
        assertEquals(List.of(evidence), edge.provenance());
    }

    @Test
    void unresolvedTargetDoesNotRequireAMaterializedNode() {
        NodeRef source = NodeRef.resolved(NodeId.of("com.example.OrderRepository"));
        NodeRef target = NodeRef.unresolved("external JDBC datasource, not modeled as a node");

        Edge edge = Edge.of(source, target, RelationType.COMMUNICATES, List.of());

        assertEquals(target, edge.target());
    }

    @Test
    void edgesWithDifferentEvidenceAreDistinctMultigraphEntries() {
        NodeRef source = NodeRef.resolved(NodeId.of("a"));
        NodeRef target = NodeRef.resolved(NodeId.of("b"));
        Evidence first = Evidence.of("java", "call site 1", ExtractionFidelity.L1_SYNTAX);
        Evidence second = Evidence.of("java", "call site 2", ExtractionFidelity.L1_SYNTAX);

        Edge edgeOne = Edge.of(source, target, RelationType.CALL, List.of(first));
        Edge edgeTwo = Edge.of(source, target, RelationType.CALL, List.of(second));

        assertNotEquals(edgeOne, edgeTwo);
    }
}
