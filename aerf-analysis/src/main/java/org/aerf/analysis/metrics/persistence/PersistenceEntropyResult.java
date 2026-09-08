package org.aerf.analysis.metrics.persistence;

import org.aerf.model.Edge;

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
     * shape. Appendix B separately calls this metric "Evidence-weighted
     * N+1 patterns / relevant persistence contexts," but no weighting
     * scheme is defined anywhere in v0.4 — see the increment notes for
     * why this implementation does not invent one.
     */
    public OptionalDouble value() {
        if (relevantEdges.isEmpty()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of((double) flaggedEdges.size() / relevantEdges.size());
    }
}
