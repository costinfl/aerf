package org.aerf.analysis.role;

import org.aerf.model.Edge;
import org.aerf.model.Graph;
import org.aerf.model.Node;
import org.aerf.model.NodeId;
import org.aerf.model.Role;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Implements only {@code R^(0) = R_seed} from AERF v0.4 section 3.2: role
 * assignment from a node's own evidence in isolation.
 *
 * <p>Section 3.2 also defines an iterative refinement, {@code R^(n+1) =
 * F(R^(n), G)}, which uses graph relationships (neighbor roles,
 * centrality, dependency direction) and runs to a fixed point. That step
 * is <b>not implemented here</b>; see {@link IterativeRoleInferenceEngine},
 * which wraps this class as its {@code R^(0)} and adds a bounded graph
 * refinement pass. A caller of this class alone gets seed-only
 * classification, not the full role inference model.
 *
 * <p>Determinism: rules are evaluated independently and every firing
 * rule's {@link RoleSignal} is kept; the winning role is chosen purely by
 * {@link RolePrecedence}, and signals are sorted by rule name before
 * being returned. The order the rule list was constructed in therefore
 * has no effect on the result (section 3.1, "Deterministic" and
 * "Stable").
 */
public final class SeedRoleInferenceEngine {

    private final List<RoleInferenceRule> rules;

    public SeedRoleInferenceEngine(List<RoleInferenceRule> rules) {
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
    }

    public RoleInferenceResult inferSeedRole(Node node) {
        Objects.requireNonNull(node, "node");

        List<RoleSignal> signals = new ArrayList<>();
        for (RoleInferenceRule rule : rules) {
            rule.evaluate(node).ifPresent(candidate ->
                    signals.add(new RoleSignal(candidate.role(), rule.name(), candidate.rationale())));
        }
        signals.sort(Comparator.comparing(RoleSignal::ruleName));

        return new RoleInferenceResult(node.id(), RolePrecedence.winner(signals), signals);
    }

    /** Every node in the graph classified independently, in the graph's own node order. */
    public Map<NodeId, RoleInferenceResult> inferAll(Graph graph) {
        Objects.requireNonNull(graph, "graph");
        Map<NodeId, RoleInferenceResult> results = new LinkedHashMap<>();
        for (Node node : graph.nodes()) {
            results.put(node.id(), inferSeedRole(node));
        }
        return Collections.unmodifiableMap(results);
    }

    /**
     * Returns a new graph with every node's role replaced by its seed
     * inference result. Any role the input graph's nodes already carried
     * is discarded: this method models the "Role inference" pipeline
     * stage (section 7), which determines role from evidence rather than
     * from whatever an upstream stage happened to set.
     */
    public Graph inferAndApply(Graph graph) {
        Objects.requireNonNull(graph, "graph");
        Graph.Builder builder = Graph.builder();
        for (Node node : graph.nodes()) {
            builder.addNode(node.withRole(inferSeedRole(node).role()));
        }
        for (Edge edge : graph.edges()) {
            builder.addEdge(edge);
        }
        return builder.build();
    }
}
