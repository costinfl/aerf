package org.aerf.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Canonical graph, {@code G = (V, E, tau_V, tau_E, rho, mu)} per AERF v0.4
 * section 2.1. This increment models {@code V} and {@code E} together with
 * the type/role/relation assignments carried on each node and edge;
 * {@code mu} (derived metrics) is out of scope until metric computation is
 * implemented.
 *
 * <p>A {@code Graph} is immutable once built. Node and edge order is
 * insertion order (not hash order), so that iterating a graph is
 * deterministic given deterministic construction, per the framework's
 * determinism principle (section 14).
 */
public final class Graph {

    private final Map<NodeId, Node> nodes;
    private final List<Edge> edges;

    private Graph(Map<NodeId, Node> nodes, List<Edge> edges) {
        this.nodes = nodes;
        this.edges = edges;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<Node> node(NodeId id) {
        return Optional.ofNullable(nodes.get(id));
    }

    public Collection<Node> nodes() {
        return nodes.values();
    }

    public List<Edge> edges() {
        return edges;
    }

    public List<Edge> edgesFrom(NodeId id) {
        List<Edge> result = new ArrayList<>();
        for (Edge edge : edges) {
            if (edge.source() instanceof NodeRef.Resolved resolved && resolved.id().equals(id)) {
                result.add(edge);
            }
        }
        return List.copyOf(result);
    }

    public List<Edge> edgesTo(NodeId id) {
        List<Edge> result = new ArrayList<>();
        for (Edge edge : edges) {
            if (edge.target() instanceof NodeRef.Resolved resolved && resolved.id().equals(id)) {
                result.add(edge);
            }
        }
        return List.copyOf(result);
    }

    public static final class Builder {

        private final Map<NodeId, Node> nodes = new LinkedHashMap<>();
        private final List<Edge> edges = new ArrayList<>();

        private Builder() {
        }

        public Builder addNode(Node node) {
            Objects.requireNonNull(node, "node");
            nodes.put(node.id(), node);
            return this;
        }

        public Builder addEdge(Edge edge) {
            Objects.requireNonNull(edge, "edge");
            requireKnownIfResolved(edge.source());
            requireKnownIfResolved(edge.target());
            edges.add(edge);
            return this;
        }

        public Builder addEdge(NodeRef source, NodeRef target, RelationType relation, List<Evidence> provenance) {
            return addEdge(Edge.of(source, target, relation, provenance));
        }

        private void requireKnownIfResolved(NodeRef ref) {
            if (ref instanceof NodeRef.Resolved resolved && !nodes.containsKey(resolved.id())) {
                throw new IllegalArgumentException(
                        "Edge references node id '" + resolved.id()
                                + "' that has not been added to the graph. "
                                + "Adapters must not create edges to nodes they have not evidenced "
                                + "(AERF v0.4 section 8); use NodeRef.unresolved(...) if the "
                                + "endpoint could not be identified.");
            }
        }

        public Graph build() {
            // Map.copyOf does not guarantee iteration order; a LinkedHashMap wrapped as
            // unmodifiable is used instead so node iteration stays insertion-ordered
            // and therefore deterministic given deterministic construction.
            return new Graph(Collections.unmodifiableMap(new LinkedHashMap<>(nodes)), List.copyOf(edges));
        }
    }
}
