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
 * <p><b>Iterative, not recursive</b> (open question #7, ExtractionAdapter
 * Plan Increment 17): {@link #strongConnect} simulates Tarjan's recursive
 * DFS with an explicit {@link Frame} stack instead of the JVM call stack,
 * so a dependency chain of any depth cannot overflow it. Each frame
 * tracks which of its node's adjacency-list children have already been
 * examined ({@code nextChildIndex}); the work-stack loop below performs,
 * in order, exactly the operations {@code strongConnect(v)} would have
 * performed at the same point in a recursive call — initialize on first
 * visit, examine one not-yet-examined child per iteration (recursing by
 * pushing a new frame, or updating {@code lowlink} directly when the
 * child is already on the Tarjan stack), and finish (extract an SCC if
 * {@code v} is its root, then propagate {@code lowlink} to the parent
 * frame — mirroring the line executed immediately after a recursive call
 * returns) only once every child has been examined. This produces the
 * exact same node visitation order, {@code index}/{@code lowlink}
 * assignments, and SCC list as the prior recursive version — it is a
 * mechanical translation, not a different algorithm.
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

    /**
     * Runs Tarjan's DFS from {@code root} to completion, using an
     * explicit work stack of {@link Frame}s in place of recursion.
     */
    private static void strongConnect(NodeId root, Map<NodeId, List<NodeId>> adjacency, TarjanState state) {
        Deque<Frame> work = new ArrayDeque<>();
        work.push(new Frame(root));

        while (!work.isEmpty()) {
            Frame frame = work.peek();
            NodeId v = frame.node;

            if (!frame.initialized) {
                state.index.put(v, state.counter);
                state.lowlink.put(v, state.counter);
                state.counter++;
                state.stack.push(v);
                state.onStack.add(v);
                frame.initialized = true;
            }

            List<NodeId> children = adjacency.getOrDefault(v, List.of());
            if (frame.nextChildIndex < children.size()) {
                NodeId w = children.get(frame.nextChildIndex);
                frame.nextChildIndex++;
                if (!state.index.containsKey(w)) {
                    work.push(new Frame(w));
                } else if (state.onStack.contains(w)) {
                    state.lowlink.put(v, Math.min(state.lowlink.get(v), state.index.get(w)));
                }
                continue;
            }

            // Every child of v has been examined - v is finished, exactly
            // the point a recursive strongConnect(v) call would return.
            work.pop();
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
            if (!work.isEmpty()) {
                // The line a recursive call's caller runs immediately after
                // strongConnect(v) returns: propagate v's final lowlink to
                // its parent frame, now back on top of the work stack.
                NodeId parent = work.peek().node;
                state.lowlink.put(parent, Math.min(state.lowlink.get(parent), state.lowlink.get(v)));
            }
        }
    }

    private static final class Frame {
        final NodeId node;
        boolean initialized;
        int nextChildIndex;

        Frame(NodeId node) {
            this.node = node;
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
