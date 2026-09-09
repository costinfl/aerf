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
    void aVeryDeepLinearChainDoesNotOverflowTheStack() {
        // Deep enough that the prior recursive strongConnect(v) would
        // reliably StackOverflowError on a default JVM stack (empirically
        // a few thousand frames) - this is the direct regression test for
        // open question #7's iterative rewrite (ExtractionAdapter Plan
        // Increment 17), not just a correctness check.
        int depth = 200_000;
        Graph.Builder builder = Graph.builder();
        for (int i = 0; i < depth; i++) {
            builder.addNode(node("n" + i));
        }
        for (int i = 0; i < depth - 1; i++) {
            builder.addEdge(NodeRef.resolved(NodeId.of("n" + i)), NodeRef.resolved(NodeId.of("n" + (i + 1))),
                    RelationType.CALL, List.of());
        }
        Graph graph = builder.build();

        List<Set<NodeId>> sccs = StronglyConnectedComponents.find(graph, EnumSet.of(RelationType.CALL));

        assertEquals(depth, sccs.size());
        assertTrue(sccs.stream().allMatch(scc -> scc.size() == 1));
    }

    @Test
    void aVeryDeepChainFeedingIntoACycleAtItsEndIsStillFoundCorrectly() {
        // Depth alone proved Increment 17's rewrite doesn't crash; this
        // proves it is still correct at depth, not merely non-crashing -
        // the cycle at the far end of a long chain must still be found,
        // and lowlink must still propagate correctly across 200,000
        // frames' worth of unwinding.
        int chainLength = 200_000;
        Graph.Builder builder = Graph.builder();
        for (int i = 0; i < chainLength; i++) {
            builder.addNode(node("n" + i));
        }
        builder.addNode(node("cycleA")).addNode(node("cycleB"));
        for (int i = 0; i < chainLength - 1; i++) {
            builder.addEdge(NodeRef.resolved(NodeId.of("n" + i)), NodeRef.resolved(NodeId.of("n" + (i + 1))),
                    RelationType.CALL, List.of());
        }
        builder.addEdge(NodeRef.resolved(NodeId.of("n" + (chainLength - 1))), NodeRef.resolved(NodeId.of("cycleA")),
                RelationType.CALL, List.of());
        builder.addEdge(NodeRef.resolved(NodeId.of("cycleA")), NodeRef.resolved(NodeId.of("cycleB")),
                RelationType.CALL, List.of());
        builder.addEdge(NodeRef.resolved(NodeId.of("cycleB")), NodeRef.resolved(NodeId.of("cycleA")),
                RelationType.CALL, List.of());
        Graph graph = builder.build();

        List<Set<NodeId>> sccs = StronglyConnectedComponents.find(graph, EnumSet.of(RelationType.CALL));

        long nonTrivial = sccs.stream().filter(scc -> scc.size() > 1).count();
        assertEquals(1, nonTrivial);
        Set<NodeId> cycle = sccs.stream().filter(scc -> scc.size() > 1).findFirst().orElseThrow();
        assertEquals(Set.of(NodeId.of("cycleA"), NodeId.of("cycleB")), cycle);
        // chainLength trivial singletons (n0..n_{chainLength-1}) plus one
        // non-trivial SCC for {cycleA, cycleB}.
        assertEquals(chainLength + 1, sccs.size());
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
