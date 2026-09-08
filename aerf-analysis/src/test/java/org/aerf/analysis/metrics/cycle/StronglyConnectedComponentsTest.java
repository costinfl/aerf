package org.aerf.analysis.metrics.cycle;

import org.aerf.model.Graph;
import org.aerf.model.Node;
import org.aerf.model.NodeId;
import org.aerf.model.NodeRef;
import org.aerf.model.NodeType;
import org.aerf.model.RelationType;
import org.aerf.model.Role;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StronglyConnectedComponentsTest {

    private static Node node(String id) {
        return Node.of(NodeId.of(id), NodeType.COMPONENT, Role.UNKNOWN, Map.of(), List.of());
    }

    @Test
    void aTwoNodeCycleIsOneComponent() {
        Graph graph = Graph.builder()
                .addNode(node("a"))
                .addNode(node("b"))
                .addEdge(NodeRef.resolved(NodeId.of("a")), NodeRef.resolved(NodeId.of("b")), RelationType.CALL, List.of())
                .addEdge(NodeRef.resolved(NodeId.of("b")), NodeRef.resolved(NodeId.of("a")), RelationType.CALL, List.of())
                .build();

        List<Set<NodeId>> sccs = StronglyConnectedComponents.find(graph, EnumSet.of(RelationType.CALL));

        assertEquals(1, sccs.size());
        assertEquals(Set.of(NodeId.of("a"), NodeId.of("b")), sccs.get(0));
    }

    @Test
    void aLinearChainIsAllTrivialSingletons() {
        Graph graph = Graph.builder()
                .addNode(node("a"))
                .addNode(node("b"))
                .addNode(node("c"))
                .addEdge(NodeRef.resolved(NodeId.of("a")), NodeRef.resolved(NodeId.of("b")), RelationType.CALL, List.of())
                .addEdge(NodeRef.resolved(NodeId.of("b")), NodeRef.resolved(NodeId.of("c")), RelationType.CALL, List.of())
                .build();

        List<Set<NodeId>> sccs = StronglyConnectedComponents.find(graph, EnumSet.of(RelationType.CALL));

        assertEquals(3, sccs.size());
        assertTrue(sccs.stream().allMatch(scc -> scc.size() == 1));
    }

    @Test
    void aNodeFeedingIntoACycleWithNoWayBackIsNotPartOfIt() {
        // d -> a -> b -> c -> a (a,b,c form a cycle; d only points in)
        Graph graph = Graph.builder()
                .addNode(node("a"))
                .addNode(node("b"))
                .addNode(node("c"))
                .addNode(node("d"))
                .addEdge(NodeRef.resolved(NodeId.of("d")), NodeRef.resolved(NodeId.of("a")), RelationType.CALL, List.of())
                .addEdge(NodeRef.resolved(NodeId.of("a")), NodeRef.resolved(NodeId.of("b")), RelationType.CALL, List.of())
                .addEdge(NodeRef.resolved(NodeId.of("b")), NodeRef.resolved(NodeId.of("c")), RelationType.CALL, List.of())
                .addEdge(NodeRef.resolved(NodeId.of("c")), NodeRef.resolved(NodeId.of("a")), RelationType.CALL, List.of())
                .build();

        List<Set<NodeId>> sccs = StronglyConnectedComponents.find(graph, EnumSet.of(RelationType.CALL));

        long nonTrivial = sccs.stream().filter(scc -> scc.size() > 1).count();
        assertEquals(1, nonTrivial);
        Set<NodeId> cycle = sccs.stream().filter(scc -> scc.size() > 1).findFirst().orElseThrow();
        assertEquals(Set.of(NodeId.of("a"), NodeId.of("b"), NodeId.of("c")), cycle);
    }

    @Test
    void edgesOutsideRelevantRelationsAreIgnored() {
        Graph graph = Graph.builder()
                .addNode(node("a"))
                .addNode(node("b"))
                .addEdge(NodeRef.resolved(NodeId.of("a")), NodeRef.resolved(NodeId.of("b")), RelationType.COMMUNICATES, List.of())
                .addEdge(NodeRef.resolved(NodeId.of("b")), NodeRef.resolved(NodeId.of("a")), RelationType.COMMUNICATES, List.of())
                .build();

        List<Set<NodeId>> sccs = StronglyConnectedComponents.find(graph, EnumSet.of(RelationType.CALL));

        assertTrue(sccs.stream().allMatch(scc -> scc.size() == 1), "COMMUNICATES is not in the relevant relation set");
    }

    @Test
    void anUnresolvedEndpointNeverParticipates() {
        Graph graph = Graph.builder()
                .addNode(node("a"))
                .addEdge(NodeRef.resolved(NodeId.of("a")), NodeRef.unresolved("dynamic"), RelationType.CALL, List.of())
                .build();

        List<Set<NodeId>> sccs = StronglyConnectedComponents.find(graph, EnumSet.of(RelationType.CALL));

        assertEquals(1, sccs.size());
        assertEquals(Set.of(NodeId.of("a")), sccs.get(0));
    }
}
