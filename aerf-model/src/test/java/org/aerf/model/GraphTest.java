package org.aerf.model;

import org.aerf.model.fixtures.CanonicalSampleGraphs;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphTest {

    @Test
    void builderRejectsAnEdgeReferencingAResolvedNodeThatWasNeverAdded() {
        Graph.Builder builder = Graph.builder()
                .addNode(Node.withUnknownRole(NodeId.of("a"), NodeType.COMPONENT, java.util.Map.of(), List.of()));

        NodeRef danglingTarget = NodeRef.resolved(NodeId.of("never-added"));

        assertThrows(IllegalArgumentException.class, () ->
                builder.addEdge(NodeRef.resolved(NodeId.of("a")), danglingTarget, RelationType.CALL, List.of()));
    }

    @Test
    void builderAcceptsAnUnresolvedTargetWithoutRequiringANode() {
        Graph graph = Graph.builder()
                .addNode(Node.withUnknownRole(NodeId.of("a"), NodeType.COMPONENT, java.util.Map.of(), List.of()))
                .addEdge(NodeRef.resolved(NodeId.of("a")), NodeRef.unresolved("dynamic target"), RelationType.CALL, List.of())
                .build();

        assertEquals(1, graph.edges().size());
    }

    @Test
    void nodeIterationOrderIsInsertionOrderNotHashOrder() {
        // Ids chosen so natural String hashing would not preserve this order.
        List<String> insertionOrder = List.of("zeta", "alpha", "mid", "beta");

        Graph graph = buildWithIds(insertionOrder);

        List<String> observedOrder = new ArrayList<>();
        graph.nodes().forEach(n -> observedOrder.add(n.id().value()));

        assertEquals(insertionOrder, observedOrder);
    }

    @Test
    void constructingTheSameGraphTwiceProducesTheSameIterationOrder() {
        List<String> insertionOrder = List.of("zeta", "alpha", "mid", "beta");

        Graph first = buildWithIds(insertionOrder);
        Graph second = buildWithIds(insertionOrder);

        List<String> firstOrder = new ArrayList<>();
        first.nodes().forEach(n -> firstOrder.add(n.id().value()));
        List<String> secondOrder = new ArrayList<>();
        second.nodes().forEach(n -> secondOrder.add(n.id().value()));

        assertEquals(firstOrder, secondOrder);
    }

    private static Graph buildWithIds(List<String> ids) {
        Graph.Builder builder = Graph.builder();
        for (String id : ids) {
            builder.addNode(Node.withUnknownRole(NodeId.of(id), NodeType.COMPONENT, java.util.Map.of(), List.of()));
        }
        return builder.build();
    }

    @Test
    void edgesFromAndEdgesToFilterByResolvedEndpointOnly() {
        Graph graph = CanonicalSampleGraphs.layeredOrderSlice();

        List<Edge> fromController = graph.edgesFrom(CanonicalSampleGraphs.ORDER_CONTROLLER);
        List<Edge> toRepository = graph.edgesTo(CanonicalSampleGraphs.ORDER_REPOSITORY);

        assertEquals(2, fromController.size(), "controller calls both the service and (violating) the repository");
        assertEquals(2, toRepository.size(), "repository is called from both the service and the controller");
    }

    @Test
    void sampleGraphPreservesTheUnresolvedExternalEdgeWithoutFabricatingANode() {
        Graph graph = CanonicalSampleGraphs.layeredOrderSlice();

        List<Edge> fromRepository = graph.edgesFrom(CanonicalSampleGraphs.ORDER_REPOSITORY);
        boolean hasUnresolvedCommunication = fromRepository.stream()
                .anyMatch(e -> e.relation() == RelationType.COMMUNICATES && e.target() instanceof NodeRef.Unresolved);

        assertTrue(hasUnresolvedCommunication);
        assertEquals(4, graph.nodes().size(), "the unresolved external system must not become a fifth node");
    }
}
