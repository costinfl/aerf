package org.aerf.analysis.metrics.layer;

import org.aerf.model.Edge;
import org.aerf.model.Graph;
import org.aerf.model.Node;
import org.aerf.model.NodeRef;
import org.aerf.model.RelationType;
import org.aerf.model.Role;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Computes layer entropy, {@code E_L = violating relevant edges / total
 * relevant edges} (AERF v0.4 section 4.1), against a {@link LayerPolicy}.
 *
 * <p>An edge is <b>relevant</b> only if: its relation is one of the
 * configured layer-relevant relations; both endpoints are
 * {@link NodeRef.Resolved} to a node actually present in the graph; and
 * both endpoints' roles are known to the policy ({@link
 * LayerPolicy#knowsRole(Role)}). An edge failing any of these is excluded
 * from both the numerator and denominator — it is outside this metric's
 * measurement universe, not silently treated as compliant. This
 * preserves section 3.5's requirement that unresolved or unclassified
 * information remain visibly separate from a measured result rather than
 * being folded into it either way.
 */
public final class LayerEntropyCalculator {

    private final LayerPolicy policy;
    private final Set<RelationType> relevantRelations;

    public LayerEntropyCalculator(LayerPolicy policy, Set<RelationType> relevantRelations) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.relevantRelations = Set.copyOf(Objects.requireNonNull(relevantRelations, "relevantRelations"));
    }

    /**
     * Uses {@code CALL} and {@code DEPENDS} as the layer-relevant relations —
     * exactly the set section 6.3's own worked invariant example
     * (`edge.label in [CALL, DEPENDS]`) uses for the same kind of check.
     */
    public static LayerEntropyCalculator withCallAndDependsRelations(LayerPolicy policy) {
        return new LayerEntropyCalculator(policy, EnumSet.of(RelationType.CALL, RelationType.DEPENDS));
    }

    public LayerEntropyResult compute(Graph graph) {
        Objects.requireNonNull(graph, "graph");

        List<Edge> relevant = new ArrayList<>();
        List<Edge> violating = new ArrayList<>();

        for (Edge edge : graph.edges()) {
            if (!relevantRelations.contains(edge.relation())) {
                continue;
            }
            Optional<Role> sourceRole = resolveRole(graph, edge.source());
            Optional<Role> targetRole = resolveRole(graph, edge.target());
            if (sourceRole.isEmpty() || targetRole.isEmpty()) {
                continue;
            }
            if (!policy.knowsRole(sourceRole.get()) || !policy.knowsRole(targetRole.get())) {
                continue;
            }

            relevant.add(edge);
            if (!policy.isAllowed(sourceRole.get(), targetRole.get())) {
                violating.add(edge);
            }
        }

        return new LayerEntropyResult(relevant, violating);
    }

    private static Optional<Role> resolveRole(Graph graph, NodeRef ref) {
        if (ref instanceof NodeRef.Resolved resolved) {
            return graph.node(resolved.id()).map(Node::role);
        }
        return Optional.empty();
    }
}
