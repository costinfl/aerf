package org.aerf.analysis.metrics.cycle;

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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CycleEntropyCalculatorTest {

    private static Node node(String id) {
        return Node.of(NodeId.of(id), NodeType.COMPONENT, Role.UNKNOWN, Map.of(), List.of());
    }

    @Test
    void twoNodeCyclePlusOneIsolatedNodeGivesTwoThirds() {
        Graph graph = Graph.builder()
                .addNode(node("a"))
                .addNode(node("b"))
                .addNode(node("c"))
                .addEdge(NodeRef.resolved(NodeId.of("a")), NodeRef.resolved(NodeId.of("b")), RelationType.CALL, List.of())
                .addEdge(NodeRef.resolved(NodeId.of("b")), NodeRef.resolved(NodeId.of("a")), RelationType.CALL, List.of())
                .build();

        CycleEntropyResult result = CycleEntropyCalculator.withCallAndDependsRelations(false).compute(graph);

        assertEquals(1, result.relevantSccs().size());
        assertEquals(Set.of(NodeId.of("a"), NodeId.of("b")), result.participatingNodes());
        assertEquals(OptionalDouble.of(2.0 / 3.0), result.value());
    }

    @Test
    void aDagHasZeroCycleEntropyDefinedNotUndefined() {
        Graph graph = Graph.builder()
                .addNode(node("a"))
                .addNode(node("b"))
                .addNode(node("c"))
                .addEdge(NodeRef.resolved(NodeId.of("a")), NodeRef.resolved(NodeId.of("b")), RelationType.CALL, List.of())
                .addEdge(NodeRef.resolved(NodeId.of("b")), NodeRef.resolved(NodeId.of("c")), RelationType.CALL, List.of())
                .build();

        CycleEntropyResult result = CycleEntropyCalculator.withCallAndDependsRelations(false).compute(graph);

        assertTrue(result.relevantSccs().isEmpty());
        assertEquals(OptionalDouble.of(0.0), result.value());
    }

    @Test
    void selfCycleIsExcludedByDefault() {
        Graph graph = Graph.builder()
                .addNode(node("a"))
                .addEdge(NodeRef.resolved(NodeId.of("a")), NodeRef.resolved(NodeId.of("a")), RelationType.CALL, List.of())
                .build();

        CycleEntropyResult result = CycleEntropyCalculator.withCallAndDependsRelations(false).compute(graph);

        assertTrue(result.relevantSccs().isEmpty(), "trivial single-node SCC, self-cycles not governed");
        assertEquals(OptionalDouble.of(0.0), result.value());
    }

    @Test
    void selfCycleCountsWhenExplicitlyGoverned() {
        Graph graph = Graph.builder()
                .addNode(node("a"))
                .addEdge(NodeRef.resolved(NodeId.of("a")), NodeRef.resolved(NodeId.of("a")), RelationType.CALL, List.of())
                .build();

        CycleEntropyResult result = CycleEntropyCalculator.withCallAndDependsRelations(true).compute(graph);

        assertEquals(1, result.relevantSccs().size());
        assertEquals(OptionalDouble.of(1.0), result.value());
    }

    @Test
    void graphWithNoNodesYieldsAnUndefinedNotZeroValue() {
        Graph graph = Graph.builder().build();

        CycleEntropyResult result = CycleEntropyCalculator.withCallAndDependsRelations(false).compute(graph);

        assertTrue(result.value().isEmpty());
    }

    @Test
    void theFixtureGraphIsACyclicAndScoresZero() {
        Graph graph = CanonicalSampleGraphs.layeredOrderSlice();

        CycleEntropyResult result = CycleEntropyCalculator.withCallAndDependsRelations(false).compute(graph);

        assertTrue(result.relevantSccs().isEmpty());
        assertEquals(OptionalDouble.of(0.0), result.value());
    }

    @Test
    void resultIsDeterministicAcrossRepeatedRuns() {
        Graph graph = Graph.builder()
                .addNode(node("a"))
                .addNode(node("b"))
                .addNode(node("c"))
                .addEdge(NodeRef.resolved(NodeId.of("a")), NodeRef.resolved(NodeId.of("b")), RelationType.CALL, List.of())
                .addEdge(NodeRef.resolved(NodeId.of("b")), NodeRef.resolved(NodeId.of("c")), RelationType.CALL, List.of())
                .addEdge(NodeRef.resolved(NodeId.of("c")), NodeRef.resolved(NodeId.of("a")), RelationType.CALL, List.of())
                .build();

        CycleEntropyCalculator calculator = CycleEntropyCalculator.withCallAndDependsRelations(false);

        assertEquals(calculator.compute(graph), calculator.compute(graph));
    }
}
