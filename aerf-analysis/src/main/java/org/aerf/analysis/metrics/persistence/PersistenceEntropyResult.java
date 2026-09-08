package org.aerf.analysis.metrics.persistence;

import org.aerf.model.Edge;
import org.aerf.model.ExecutionContext;

import java.util.List;
import java.util.OptionalDouble;

/**
 * The result of computing the basic N+1 persistence heuristic (AERF v0.4
 * section 4.3) over one graph. {@code flaggedEdges} and
 * {@code relevantEdges} are kept in full, not just their counts, so a
 * finding stays traceable to the specific call site(s) that produced it
 * — section 4.3 explicitly warns "this is a heuristic, not a claim that
 * every repeated query is an N+1 defect," so a consumer must be able to
 * go back to the evidence rather than trust a bare ratio.
 */
public record PersistenceEntropyResult(List<Edge> relevantEdges, List<Edge> flaggedEdges) {

    public PersistenceEntropyResult {
        relevantEdges = List.copyOf(relevantEdges);
        flaggedEdges = List.copyOf(flaggedEdges);
    }

    /**
     * {@code flagged persistence contexts / total relevant persistence
     * contexts}. Empty when there are no relevant persistence contexts at
     * all: undefined, not zero, for the same reason as layer and cycle
     * entropy.
     *
     * <p>This is a plain count ratio, matching section 4.3's own formula
     * shape, and is distinct from {@link #weightedValue()} — both remain
     * separately visible per the "measurement before aggregation"
     * principle rather than collapsing into one number.
     */
    public OptionalDouble value() {
        if (relevantEdges.isEmpty()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of((double) flaggedEdges.size() / relevantEdges.size());
    }

    /**
     * The "Evidence-weighted N+1 patterns / relevant persistence contexts"
     * score from Appendix B, as concretely defined by AERF v0.4.1 patch
     * Amendment 3: each relevant edge's weight is the count of its
     * provenance entries with {@link ExecutionContext#ITERATED}, and this
     * value is {@code sum(weight) / count(relevantEdges)} — the
     * denominator stays an unweighted count, as Appendix B states.
     *
     * <p>Equal to {@link #value()} exactly when every flagged edge has
     * precisely one {@code ITERATED} evidence item; strictly greater
     * whenever a flagged edge is backed by more than one independently
     * observed iterated call site, since such an edge then contributes
     * more than 1 to the numerator instead of a flat 1.
     */
    public OptionalDouble weightedValue() {
        if (relevantEdges.isEmpty()) {
            return OptionalDouble.empty();
        }
        long totalWeight = flaggedEdges.stream()
                .mapToLong(edge -> edge.provenance().stream()
                        .filter(evidence -> evidence.executionContext() == ExecutionContext.ITERATED)
                        .count())
                .sum();
        return OptionalDouble.of((double) totalWeight / relevantEdges.size());
    }
}
