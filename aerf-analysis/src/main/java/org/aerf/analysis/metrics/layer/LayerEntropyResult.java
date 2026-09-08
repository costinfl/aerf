package org.aerf.analysis.metrics.layer;

import org.aerf.model.Edge;

import java.util.List;
import java.util.OptionalDouble;

/**
 * The result of computing layer entropy (AERF v0.4 section 4.1) over one
 * graph. {@code violatingEdges} and {@code relevantEdges} are kept in
 * full, not just their counts, so a finding remains traceable to the
 * edges (and, through them, the evidence) that produced it.
 */
public record LayerEntropyResult(List<Edge> relevantEdges, List<Edge> violatingEdges) {

    public LayerEntropyResult {
        relevantEdges = List.copyOf(relevantEdges);
        violatingEdges = List.copyOf(violatingEdges);
    }

    /**
     * {@code violating relevant edges / total relevant edges}, per section
     * 4.1. Empty when there are no relevant edges: the entropy dimension is
     * undefined, not zero. Reporting 0.0 in that situation would silently
     * claim "no deviation detected" when in fact nothing was measurable,
     * violating the principle that absence of evidence must stay visible
     * rather than be converted into an apparently-confident result.
     */
    public OptionalDouble value() {
        if (relevantEdges.isEmpty()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of((double) violatingEdges.size() / relevantEdges.size());
    }
}
