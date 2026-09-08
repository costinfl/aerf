package org.aerf.analysis.invariant;

import org.aerf.analysis.invariant.examples.SpecWorkedExamples;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvariantEvaluatorEdgeScopeTest {

    private final InvariantEvaluator evaluator = new InvariantEvaluator();
    private final Invariant noPresentationToPersistence = SpecWorkedExamples.noPresentationToPersistence();

    @Test
    void theFixtureGraphHasExactlyOneViolationTheDeliberateOne() {
        Graph graph = CanonicalSampleGraphs.layeredOrderSlice();

        InvariantEvaluationResult result = evaluator.evaluate(noPresentationToPersistence, graph, Map.of());

        assertEquals(1, result.violations().size());
        ViolationSubject.OfEdge violation = (ViolationSubject.OfEdge) result.violations().get(0).subject();
        assertEquals(NodeRef.resolved(CanonicalSampleGraphs.ORDER_CONTROLLER), violation.edge().source());
        assertEquals(NodeRef.resolved(CanonicalSampleGraphs.ORDER_REPOSITORY), violation.edge().target());
        assertEquals("critical", result.severity());
    }

    @Test
    void anEdgeWithAnUnresolvedEndpointIsExcludedNotCountedEitherWay() {
        Node controller = Node.of(NodeId.of("controller"), NodeType.COMPONENT, Role.PRESENTATION, Map.of(), List.of());

        Graph graph = Graph.builder()
                .addNode(controller)
                .addEdge(NodeRef.resolved(controller.id()), NodeRef.unresolved("dynamic proxy, resolves to something unknown"),
                        RelationType.CALL, List.of())
                .build();

        InvariantEvaluationResult result = evaluator.evaluate(noPresentationToPersistence, graph, Map.of());

        assertTrue(result.holds(), "an edge whose target role cannot even be determined must not be judged");
    }

    @Test
    void anEdgeExcludedByTheWhenFilterNeverReachesTheAssertion() {
        Node controller = Node.of(NodeId.of("controller"), NodeType.COMPONENT, Role.PRESENTATION, Map.of(), List.of());
        Node repository = Node.of(NodeId.of("repository"), NodeType.COMPONENT, Role.PERSISTENCE, Map.of(), List.of());

        // Same role pair as the violation case, but via COMMUNICATES, which
        // the invariant's own "when" clause does not cover.
        Graph graph = Graph.builder()
                .addNode(controller)
                .addNode(repository)
                .addEdge(NodeRef.resolved(controller.id()), NodeRef.resolved(repository.id()), RelationType.COMMUNICATES, List.of())
                .build();

        InvariantEvaluationResult result = evaluator.evaluate(noPresentationToPersistence, graph, Map.of());

        assertTrue(result.holds());
    }
}
