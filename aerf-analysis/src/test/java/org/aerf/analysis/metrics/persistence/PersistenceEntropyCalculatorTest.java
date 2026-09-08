package org.aerf.analysis.metrics.persistence;

import org.aerf.model.Evidence;
import org.aerf.model.ExecutionContext;
import org.aerf.model.ExtractionFidelity;
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

class PersistenceEntropyCalculatorTest {

    private static Node node(String id, NodeType type, Role role) {
        return Node.of(NodeId.of(id), type, role, Map.of(), List.of());
    }

    private final PersistenceEntropyCalculator calculator = PersistenceEntropyCalculator.withCallRelation();

    @Test
    void anIteratedCallToAPersistenceTargetIsFlagged() {
        Graph graph = Graph.builder()
                .addNode(node("service", NodeType.COMPONENT, Role.APPLICATION))
                .addNode(node("repo", NodeType.COMPONENT, Role.PERSISTENCE))
                .addEdge(NodeRef.resolved(NodeId.of("service")), NodeRef.resolved(NodeId.of("repo")), RelationType.CALL,
                        List.of(Evidence.of("java", "call inside for loop", ExtractionFidelity.L2_SYMBOL_RESOLVED, ExecutionContext.ITERATED)))
                .build();

        PersistenceEntropyResult result = calculator.compute(graph);

        assertEquals(1, result.relevantEdges().size());
        assertEquals(1, result.flaggedEdges().size());
        assertEquals(OptionalDouble.of(1.0), result.value());
    }

    @Test
    void aSingleExecutionCallToAPersistenceTargetIsNotFlagged() {
        Graph graph = Graph.builder()
                .addNode(node("service", NodeType.COMPONENT, Role.APPLICATION))
                .addNode(node("repo", NodeType.COMPONENT, Role.PERSISTENCE))
                .addEdge(NodeRef.resolved(NodeId.of("service")), NodeRef.resolved(NodeId.of("repo")), RelationType.CALL,
                        List.of(Evidence.of("java", "call outside any loop", ExtractionFidelity.L2_SYMBOL_RESOLVED, ExecutionContext.SINGLE)))
                .build();

        PersistenceEntropyResult result = calculator.compute(graph);

        assertEquals(1, result.relevantEdges().size());
        assertTrue(result.flaggedEdges().isEmpty());
        assertEquals(OptionalDouble.of(0.0), result.value());
    }

    @Test
    void evidenceWithNoExecutionContextClaimIsNotFlagged() {
        // Plain Evidence.of(...) without an explicit ExecutionContext defaults to
        // UNKNOWN, which must not be treated as ITERATED (see Evidence's javadoc).
        Graph graph = Graph.builder()
                .addNode(node("service", NodeType.COMPONENT, Role.APPLICATION))
                .addNode(node("repo", NodeType.COMPONENT, Role.PERSISTENCE))
                .addEdge(NodeRef.resolved(NodeId.of("service")), NodeRef.resolved(NodeId.of("repo")), RelationType.CALL,
                        List.of(Evidence.of("java", "call observed", ExtractionFidelity.L1_SYNTAX)))
                .build();

        PersistenceEntropyResult result = calculator.compute(graph);

        assertTrue(result.flaggedEdges().isEmpty());
    }

    @Test
    void callsToNonPersistenceRolesAreExcludedFromRelevantEntirely() {
        Graph graph = Graph.builder()
                .addNode(node("controller", NodeType.COMPONENT, Role.PRESENTATION))
                .addNode(node("service", NodeType.COMPONENT, Role.APPLICATION))
                .addEdge(NodeRef.resolved(NodeId.of("controller")), NodeRef.resolved(NodeId.of("service")), RelationType.CALL,
                        List.of(Evidence.of("java", "call inside for loop", ExtractionFidelity.L2_SYMBOL_RESOLVED, ExecutionContext.ITERATED)))
                .build();

        PersistenceEntropyResult result = calculator.compute(graph);

        assertTrue(result.relevantEdges().isEmpty(), "Application is not Persistence, so this is not a persistence context at all");
    }

    @Test
    void relationsOutsideTheConfiguredSetAreExcluded() {
        Graph graph = Graph.builder()
                .addNode(node("service", NodeType.COMPONENT, Role.APPLICATION))
                .addNode(node("order", NodeType.DATA, Role.PERSISTENCE))
                .addEdge(NodeRef.resolved(NodeId.of("service")), NodeRef.resolved(NodeId.of("order")), RelationType.DEPENDS, List.of())
                .build();

        PersistenceEntropyResult result = calculator.compute(graph);

        assertTrue(result.relevantEdges().isEmpty(), "DEPENDS is not in the default relevant relation set");
    }

    @Test
    void unresolvedTargetsAreExcluded() {
        Graph graph = Graph.builder()
                .addNode(node("service", NodeType.COMPONENT, Role.APPLICATION))
                .addEdge(NodeRef.resolved(NodeId.of("service")), NodeRef.unresolved("dynamic proxy target"), RelationType.CALL, List.of())
                .build();

        PersistenceEntropyResult result = calculator.compute(graph);

        assertTrue(result.relevantEdges().isEmpty());
    }

    @Test
    void graphWithNoRelevantEdgesYieldsAnUndefinedNotZeroValue() {
        Graph graph = Graph.builder()
                .addNode(node("solo", NodeType.COMPONENT, Role.PERSISTENCE))
                .build();

        PersistenceEntropyResult result = calculator.compute(graph);

        assertTrue(result.value().isEmpty());
    }

    @Test
    void mixOfFlaggedAndUnflaggedProducesTheExpectedRatio() {
        Graph.Builder builder = Graph.builder()
                .addNode(node("service", NodeType.COMPONENT, Role.APPLICATION))
                .addNode(node("repoA", NodeType.COMPONENT, Role.PERSISTENCE))
                .addNode(node("repoB", NodeType.COMPONENT, Role.PERSISTENCE))
                .addNode(node("repoC", NodeType.COMPONENT, Role.PERSISTENCE));
        builder.addEdge(NodeRef.resolved(NodeId.of("service")), NodeRef.resolved(NodeId.of("repoA")), RelationType.CALL,
                List.of(Evidence.of("java", "iterated call", ExtractionFidelity.L2_SYMBOL_RESOLVED, ExecutionContext.ITERATED)));
        builder.addEdge(NodeRef.resolved(NodeId.of("service")), NodeRef.resolved(NodeId.of("repoB")), RelationType.CALL,
                List.of(Evidence.of("java", "single call", ExtractionFidelity.L2_SYMBOL_RESOLVED, ExecutionContext.SINGLE)));
        builder.addEdge(NodeRef.resolved(NodeId.of("service")), NodeRef.resolved(NodeId.of("repoC")), RelationType.CALL,
                List.of(Evidence.of("java", "unknown context call", ExtractionFidelity.L1_SYNTAX)));

        PersistenceEntropyResult result = calculator.compute(builder.build());

        assertEquals(3, result.relevantEdges().size());
        assertEquals(1, result.flaggedEdges().size());
        assertEquals(OptionalDouble.of(1.0 / 3.0), result.value());
    }

    @Test
    void theFixtureGraphsExistingCallsAreNotFlaggedSinceNoneClaimIteration() {
        Graph graph = CanonicalSampleGraphs.layeredOrderSlice();

        PersistenceEntropyResult result = calculator.compute(graph);

        assertEquals(2, result.relevantEdges().size(), "service->repository and controller->repository are both CALLs to a Persistence node");
        assertTrue(result.flaggedEdges().isEmpty());
        assertEquals(OptionalDouble.of(0.0), result.value());
    }

    @Test
    void resultIsDeterministicAcrossRepeatedRuns() {
        Graph graph = CanonicalSampleGraphs.layeredOrderSlice();

        assertEquals(calculator.compute(graph), calculator.compute(graph));
    }
}
