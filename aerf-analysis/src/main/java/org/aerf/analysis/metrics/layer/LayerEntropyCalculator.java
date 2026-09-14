package org.aerf.analysis.metrics.layer;

import org.aerf.analysis.calibration.DimensionConfidence;
import org.aerf.analysis.governance.SubsystemLayerPolicies;
import org.aerf.analysis.governance.SubsystemLayerPolicy;
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
import java.util.OptionalDouble;
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
 *
 * <p><b>Which matrix judges an edge</b> (Increment 26, OQ-04). An
 * organization may give a subsystem its own {@link LayerPolicy}. When it
 * has, <em>the edge's source decides</em>: layering constrains what a
 * component may depend on, so the source is the party whose declared
 * rules are being tested, and a cross-subsystem edge is judged by the
 * matrix its caller declared. An edge is therefore always governed by
 * exactly one matrix — never by none, and never by two — so this can
 * neither drop an edge from the denominator nor double-count it.
 * A node no subsystem claims is governed by the default matrix, and
 * declaring no subsystems at all leaves every edge governed by the
 * default, which is exactly this class's behaviour before OQ-04.
 *
 * <p>Note the relevance filter uses the <em>governing</em> policy too:
 * whether an edge is measurable at all is a question only the matrix
 * actually judging it can answer.
 */
public final class LayerEntropyCalculator {

    private final LayerPolicy defaultPolicy;
    private final SubsystemLayerPolicies subsystemPolicies;
    private final Set<RelationType> relevantRelations;

    public LayerEntropyCalculator(LayerPolicy defaultPolicy, Set<RelationType> relevantRelations) {
        this(defaultPolicy, SubsystemLayerPolicies.none(), relevantRelations);
    }

    public LayerEntropyCalculator(LayerPolicy defaultPolicy, SubsystemLayerPolicies subsystemPolicies,
                                  Set<RelationType> relevantRelations) {
        this.defaultPolicy = Objects.requireNonNull(defaultPolicy, "defaultPolicy");
        this.subsystemPolicies = Objects.requireNonNull(subsystemPolicies, "subsystemPolicies");
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

    /**
     * As {@link #withCallAndDependsRelations(LayerPolicy)}, with per-subsystem
     * matrices layered over the default one (OQ-04). The relation set is
     * identical either way — subsystem declaration changes which matrix
     * judges an edge, never which edges are in scope.
     */
    public static LayerEntropyCalculator withCallAndDependsRelations(
            LayerPolicy defaultPolicy, SubsystemLayerPolicies subsystemPolicies) {
        return new LayerEntropyCalculator(
                defaultPolicy, subsystemPolicies, EnumSet.of(RelationType.CALL, RelationType.DEPENDS));
    }

    /**
     * How much of this dimension's own evidence was resolved (OQ-13) —
     * measured over the same relation set {@link #compute} uses, but
     * without the role/policy filter, per
     * {@link DimensionConfidence}'s own documentation of why.
     */
    public OptionalDouble confidence(Graph graph) {
        return DimensionConfidence.forRelations(graph, relevantRelations);
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
            LayerPolicy governing = governingPolicy(edge.source());
            if (!governing.knowsRole(sourceRole.get()) || !governing.knowsRole(targetRole.get())) {
                continue;
            }

            relevant.add(edge);
            if (!governing.isAllowed(sourceRole.get(), targetRole.get())) {
                violating.add(edge);
            }
        }

        return new LayerEntropyResult(relevant, violating);
    }

    /**
     * The matrix judging an edge, chosen by its source. Falls back to the
     * default for a node no subsystem claims — and {@code
     * SubsystemLayerPolicies} guarantees at most one claimant, so this is
     * independent of declaration order.
     */
    private LayerPolicy governingPolicy(NodeRef source) {
        if (source instanceof NodeRef.Resolved resolved) {
            return subsystemPolicies.governing(resolved.id())
                    .map(SubsystemLayerPolicy::layerPolicy)
                    .orElse(defaultPolicy);
        }
        return defaultPolicy;
    }

    private static Optional<Role> resolveRole(Graph graph, NodeRef ref) {
        if (ref instanceof NodeRef.Resolved resolved) {
            return graph.node(resolved.id()).map(Node::role);
        }
        return Optional.empty();
    }
}
