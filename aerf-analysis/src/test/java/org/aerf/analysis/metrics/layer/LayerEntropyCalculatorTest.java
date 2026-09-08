package org.aerf.analysis.metrics.layer;

import org.aerf.model.Edge;
import org.aerf.model.Evidence;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayerEntropyCalculatorTest {

    /**
     * A hand-declared governance policy for the fixture graph. Not an AERF
     * default (none exists, see LayerPolicy's javadoc) — chosen here to
     * match the fixture's own stated intent: Presentation calling
     * Persistence directly is the deliberate violation; Application
     * calling Persistence directly (skipping Domain) is ordinary and
     * allowed.
     */
    private static LayerPolicy fixturePolicy() {
        return LayerPolicy.of(
                Set.of(Role.PRESENTATION, Role.APPLICATION, Role.DOMAIN, Role.PERSISTENCE, Role.INFRASTRUCTURE),
                Map.of(
                        Role.PRESENTATION, Set.of(Role.PRESENTATION, Role.APPLICATION),
                        Role.APPLICATION, Set.of(Role.APPLICATION, Role.DOMAIN, Role.PERSISTENCE),
                        Role.DOMAIN, Set.of(Role.DOMAIN, Role.PERSISTENCE),
                        Role.PERSISTENCE, Set.of(Role.PERSISTENCE, Role.INFRASTRUCTURE),
                        Role.INFRASTRUCTURE, Set.of(Role.INFRASTRUCTURE)));
    }

    @Test
    void fixtureGraphHasExactlyOneViolationOutOfFourRelevantEdges() {
        Graph graph = CanonicalSampleGraphs.layeredOrderSlice();
        LayerEntropyCalculator calculator = LayerEntropyCalculator.withCallAndDependsRelations(fixturePolicy());

        LayerEntropyResult result = calculator.compute(graph);

        assertEquals(4, result.relevantEdges().size(),
                "controller->service, service->order, service->repository, controller->repository");
        assertEquals(1, result.violatingEdges().size());
        assertEquals(OptionalDouble.of(0.25), result.value());

        Edge violation = result.violatingEdges().get(0);
        assertEquals(NodeRef.resolved(CanonicalSampleGraphs.ORDER_CONTROLLER), violation.source());
        assertEquals(NodeRef.resolved(CanonicalSampleGraphs.ORDER_REPOSITORY), violation.target());
    }

    @Test
    void unresolvedEndpointsAndOutOfPolicyRelationsAreExcludedNotCountedEitherWay() {
        Graph graph = CanonicalSampleGraphs.layeredOrderSlice();
        LayerEntropyCalculator calculator = LayerEntropyCalculator.withCallAndDependsRelations(fixturePolicy());

        LayerEntropyResult result = calculator.compute(graph);

        boolean anyRelevantEdgeIsTheCommunicatesEdge = result.relevantEdges().stream()
                .anyMatch(e -> e.relation() == RelationType.COMMUNICATES);
        assertTrue(!anyRelevantEdgeIsTheCommunicatesEdge, "the unresolved COMMUNICATES edge must not be counted");
    }

    @Test
    void graphWithNoRelevantEdgesYieldsAnUndefinedNotZeroValue() {
        Node isolated = Node.of(NodeId.of("solo"), NodeType.COMPONENT, Role.PRESENTATION, Map.of(), List.of());
        Graph graph = Graph.builder().addNode(isolated).build();
        LayerEntropyCalculator calculator = LayerEntropyCalculator.withCallAndDependsRelations(fixturePolicy());

        LayerEntropyResult result = calculator.compute(graph);

        assertTrue(result.relevantEdges().isEmpty());
        assertTrue(result.value().isEmpty(), "no relevant edges means the dimension is undefined, not zero");
    }

    @Test
    void anEdgeBetweenRolesUnknownToThePolicyIsExcluded() {
        Node external = Node.of(NodeId.of("ext"), NodeType.COMPONENT, Role.EXTERNAL, Map.of(),
                List.of(Evidence.of("java", "third-party client", ExtractionFidelity.L1_SYNTAX)));
        Node presentation = Node.of(NodeId.of("ui"), NodeType.COMPONENT, Role.PRESENTATION, Map.of(), List.of());

        Graph graph = Graph.builder()
                .addNode(external)
                .addNode(presentation)
                .addEdge(NodeRef.resolved(NodeId.of("ui")), NodeRef.resolved(NodeId.of("ext")), RelationType.CALL, List.of())
                .build();

        // fixturePolicy() does not declare Role.EXTERNAL at all.
        LayerEntropyResult result = LayerEntropyCalculator.withCallAndDependsRelations(fixturePolicy()).compute(graph);

        assertTrue(result.relevantEdges().isEmpty());
        assertTrue(result.value().isEmpty());
    }
}
