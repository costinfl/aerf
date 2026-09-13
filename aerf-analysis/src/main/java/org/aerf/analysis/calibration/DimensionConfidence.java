package org.aerf.analysis.calibration;

import org.aerf.model.Edge;
import org.aerf.model.Graph;
import org.aerf.model.NodeRef;
import org.aerf.model.RelationType;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Confidence scoped to one entropy dimension's own relevant evidence
 * (post-v0.4.1 backlog, OQ-13) — the per-dimension counterpart to
 * {@link AnalysisConfidence}'s graph-wide measure, which is left exactly
 * as it was.
 *
 * <p>{@link AnalysisConfidence}'s own javadoc names the tension this
 * resolves: it "is a graph-wide measure, not scoped to any one entropy
 * dimension's own relation filter." A reader told that layer entropy is
 * 0.67 and confidence is 0.38 cannot tell how much of that uncertainty
 * actually falls on the edges layer entropy measured. This answers that
 * question per dimension without changing what the graph-wide number
 * means.
 *
 * <p><b>Resolution is tested exactly as {@code AnalysisConfidence} tests
 * it</b> — both endpoints {@link NodeRef.Resolved} — so the codebase has
 * one definition of "resolved", not two that could drift.
 *
 * <p><b>Role and policy filters are excluded from both sides.</b> A
 * dimension's entropy may narrow its relevant set further than relation
 * type: layer entropy also requires both endpoint roles to be known to
 * the policy, and persistence entropy requires the target to be
 * {@code PERSISTENCE}. Those filters are deliberately <em>not</em>
 * applied here, for two reasons. AERF v0.4.1 Amendment 7 already settled
 * that a node's role outcome must never affect confidence — confidence
 * asks whether extraction resolved a reference, not whether inference
 * classified a node. And mechanically, folding persistence's
 * target-role filter into the denominator would make its confidence
 * trivially 1.0, since an edge cannot have been selected by target role
 * without its target having resolved.
 *
 * <p><b>{@code MEMBER_OF} needs no special case here.</b>
 * {@code AnalysisConfidence} excludes it explicitly because it has no
 * relation filter at all and would otherwise count every structural
 * membership edge, which is resolved by construction and so inflates the
 * ratio. A relation-scoped computation excludes it inherently: no
 * dimension's relation set contains it. Silently dropping a relation a
 * caller explicitly asked for would be the more surprising behaviour, so
 * this method measures exactly the set it is given — and a test pins that
 * no dimension's set contains {@code MEMBER_OF}.
 */
public final class DimensionConfidence {

    private DimensionConfidence() {
    }

    /**
     * @return resolved / total over the edges whose relation is in
     *     {@code relations}; {@link OptionalDouble#empty()} when the
     *     dimension has no such edges at all. Empty means "nothing was
     *     measurable", never 1.0 — the same undefined-not-certain rule
     *     every entropy result in this project already follows.
     */
    public static OptionalDouble forRelations(Graph graph, Set<RelationType> relations) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(relations, "relations");

        List<Edge> inScope = graph.edges().stream().filter(e -> relations.contains(e.relation())).toList();
        if (inScope.isEmpty()) {
            return OptionalDouble.empty();
        }

        long resolved = inScope.stream().filter(DimensionConfidence::bothEndpointsResolved).count();
        return OptionalDouble.of((double) resolved / inScope.size());
    }

    private static boolean bothEndpointsResolved(Edge edge) {
        return edge.source() instanceof NodeRef.Resolved && edge.target() instanceof NodeRef.Resolved;
    }
}
