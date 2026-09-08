package org.aerf.analysis.calibration;

import org.aerf.model.Graph;
import org.aerf.model.Node;
import org.aerf.model.NodeId;
import org.aerf.model.NodeRef;
import org.aerf.model.NodeType;
import org.aerf.model.RelationType;
import org.aerf.model.Role;
import org.aerf.model.fixtures.CanonicalSampleGraphs;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisConfidenceTest {

    @Test
    void anEmptyGraphIsUndefinedNotZeroOrOne() {
        Graph graph = Graph.builder().build();

        assertTrue(AnalysisConfidence.compute(graph).isEmpty());
    }

    @Test
    void allResolvedEdgesGiveFullConfidence() {
        Graph graph = Graph.builder()
                .addNode(Node.of(NodeId.of("a"), NodeType.COMPONENT, Role.UNKNOWN, Map.of(), List.of()))
                .addNode(Node.of(NodeId.of("b"), NodeType.COMPONENT, Role.UNKNOWN, Map.of(), List.of()))
                .addEdge(NodeRef.resolved(NodeId.of("a")), NodeRef.resolved(NodeId.of("b")), RelationType.CALL, List.of())
                .build();

        assertEquals(OptionalDouble.of(1.0), AnalysisConfidence.compute(graph));
    }

    @Test
    void oneUnresolvedEdgeOutOfTwoGivesOneHalf() {
        Graph graph = Graph.builder()
                .addNode(Node.of(NodeId.of("a"), NodeType.COMPONENT, Role.UNKNOWN, Map.of(), List.of()))
                .addNode(Node.of(NodeId.of("b"), NodeType.COMPONENT, Role.UNKNOWN, Map.of(), List.of()))
                .addEdge(NodeRef.resolved(NodeId.of("a")), NodeRef.resolved(NodeId.of("b")), RelationType.CALL, List.of())
                .addEdge(NodeRef.resolved(NodeId.of("a")), NodeRef.unresolved("dynamic dispatch target"), RelationType.CALL, List.of())
                .build();

        assertEquals(OptionalDouble.of(0.5), AnalysisConfidence.compute(graph));
    }

    @Test
    void theFixtureGraphHasFourOfFiveEdgesResolved() {
        // The layered fixture has 5 edges total; only the repository's
        // outbound COMMUNICATES edge to an external system is unresolved.
        Graph graph = CanonicalSampleGraphs.layeredOrderSlice();

        assertEquals(OptionalDouble.of(0.8), AnalysisConfidence.compute(graph));
    }
}
