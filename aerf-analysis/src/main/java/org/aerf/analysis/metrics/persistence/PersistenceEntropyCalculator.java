package org.aerf.analysis.metrics.persistence;

import org.aerf.model.Edge;
import org.aerf.model.ExecutionContext;
import org.aerf.model.Graph;
import org.aerf.model.Node;
import org.aerf.model.NodeRef;
import org.aerf.model.RelationType;
import org.aerf.model.Role;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Computes the basic N+1 heuristic from AERF v0.4 section 4.3: "repeated
 * persistence operations within iteration or repeated execution contexts
 * where access is not batched or otherwise justified."
 *
 * <p>A <b>relevant persistence context</b> is an edge whose relation is
 * in the configured set, whose target is {@link NodeRef.Resolved} to a
 * node present in the graph with {@link Role#PERSISTENCE}, and whose
 * source is likewise resolved. Among those, an edge is <b>flagged</b> if
 * any of its provenance carries {@link ExecutionContext#ITERATED}.
 *
 * <p><b>What this does not attempt:</b> section 4.3's "where access is
 * not batched or otherwise justified" clause — an explicit governance
 * exception for iterated access that is intentional (e.g. a deliberate
 * bulk-fetch loop) — is not implemented. There is no representation yet
 * of "this specific repeated access is an approved exception," which
 * belongs naturally to the invariant/exception model (section 6), not to
 * this metric. Every iterated persistence context is flagged; distinguishing
 * justified from unjustified repetition is left for whenever that model
 * exists.
 */
public final class PersistenceEntropyCalculator {

    private final Set<RelationType> relevantRelations;

    public PersistenceEntropyCalculator(Set<RelationType> relevantRelations) {
        this.relevantRelations = Set.copyOf(Objects.requireNonNull(relevantRelations, "relevantRelations"));
    }

    /**
     * Uses {@code CALL} as the only persistence-relevant relation: the
     * unambiguous shape of an N+1 pattern is a call to a persistence-role
     * operation (e.g. a repository method) made repeatedly. {@code READS}
     * and {@code WRITES} edges represent data access at a different,
     * more structural level and are not included by default — a caller
     * needing that can construct {@link #PersistenceEntropyCalculator}
     * directly with a different relation set.
     */
    public static PersistenceEntropyCalculator withCallRelation() {
        return new PersistenceEntropyCalculator(EnumSet.of(RelationType.CALL));
    }

    public PersistenceEntropyResult compute(Graph graph) {
        Objects.requireNonNull(graph, "graph");

        List<Edge> relevant = new ArrayList<>();
        List<Edge> flagged = new ArrayList<>();

        for (Edge edge : graph.edges()) {
            if (!relevantRelations.contains(edge.relation())) {
                continue;
            }
            if (!(edge.source() instanceof NodeRef.Resolved) || !(edge.target() instanceof NodeRef.Resolved targetRef)) {
                continue;
            }
            Role targetRole = graph.node(targetRef.id()).map(Node::role).orElse(null);
            if (targetRole != Role.PERSISTENCE) {
                continue;
            }

            relevant.add(edge);
            boolean iterated = edge.provenance().stream().anyMatch(e -> e.executionContext() == ExecutionContext.ITERATED);
            if (iterated) {
                flagged.add(edge);
            }
        }

        return new PersistenceEntropyResult(relevant, flagged);
    }
}
