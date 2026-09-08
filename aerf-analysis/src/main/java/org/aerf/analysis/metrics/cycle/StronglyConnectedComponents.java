package org.aerf.analysis.metrics.cycle;

import org.aerf.model.Edge;
import org.aerf.model.Graph;
import org.aerf.model.Node;
import org.aerf.model.NodeId;
import org.aerf.model.NodeRef;
import org.aerf.model.RelationType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tarjan's strongly-connected-components algorithm, restricted to edges
 * whose relation is in {@code relevantRelations} and whose endpoints are
 * both {@link NodeRef.Resolved}. Returns every SCC, trivial (single-node,
 * no self-loop) ones included; filtering those out per AERF v0.4 section
 * 4.2 is {@link CycleEntropyCalculator}'s job, not this class's.
 *
 * <p>Deterministic: the outer traversal visits nodes in the graph's own
 * (insertion-ordered) iteration order, and each node's adjacency list is
 * built in the graph's edge insertion order, so the same graph always
 * yields the same list of components in the same order.
 *
 * <p>Implemented recursively for clarity. A graph with a dependency chain
 * deep enough to exceed the JVM's call stack would overflow; an
 * iterative rewrite would be needed before this is run against a very
 * large real system. Not attempted here — see the increment's open
 * questions.
 */
final class StronglyConnectedComponents {

    private StronglyConnectedComponents() {
    }

    static List<Set<NodeId>> find(Graph graph, Set<RelationType> relevantRelations) {
        Map<NodeId, List<NodeId>> adjacency = buildAdjacency(graph, relevantRelations);

        TarjanState state = new TarjanState();
        for (Node node : graph.nodes()) {
            NodeId id = node.id();
            if (!state.index.containsKey(id)) {
                strongConnect(id, adjacency, state);
            }
        }
        return state.result;
    }

    private static void strongConnect(NodeId v, Map<NodeId, List<NodeId>> adjacency, TarjanState state) {
        state.index.put(v, state.counter);
        state.lowlink.put(v, state.counter);
        state.counter++;
        state.stack.push(v);
        state.onStack.add(v);

        for (NodeId w : adjacency.getOrDefault(v, List.of())) {
            if (!state.index.containsKey(w)) {
                strongConnect(w, adjacency, state);
                state.lowlink.put(v, Math.min(state.lowlink.get(v), state.lowlink.get(w)));
            } else if (state.onStack.contains(w)) {
                state.lowlink.put(v, Math.min(state.lowlink.get(v), state.index.get(w)));
            }
        }

        if (state.lowlink.get(v).equals(state.index.get(v))) {
            Set<NodeId> component = new LinkedHashSet<>();
            NodeId w;
            do {
                w = state.stack.pop();
                state.onStack.remove(w);
                component.add(w);
            } while (!w.equals(v));
            state.result.add(component);
        }
    }

    private static Map<NodeId, List<NodeId>> buildAdjacency(Graph graph, Set<RelationType> relevantRelations) {
        Map<NodeId, List<NodeId>> adjacency = new LinkedHashMap<>();
        for (Node node : graph.nodes()) {
            adjacency.put(node.id(), new ArrayList<>());
        }
        for (Edge edge : graph.edges()) {
            if (!relevantRelations.contains(edge.relation())) {
                continue;
            }
            if (!(edge.source() instanceof NodeRef.Resolved source) || !(edge.target() instanceof NodeRef.Resolved target)) {
                continue;
            }
            List<NodeId> targets = adjacency.get(source.id());
            if (targets != null && adjacency.containsKey(target.id())) {
                targets.add(target.id());
            }
        }
        return adjacency;
    }

    private static final class TarjanState {
        final Map<NodeId, Integer> index = new LinkedHashMap<>();
        final Map<NodeId, Integer> lowlink = new LinkedHashMap<>();
        final Set<NodeId> onStack = new LinkedHashSet<>();
        final Deque<NodeId> stack = new ArrayDeque<>();
        final List<Set<NodeId>> result = new ArrayList<>();
        int counter = 0;
    }
}
