package org.aerf.analysis.calibration;

import org.aerf.model.Edge;
import org.aerf.model.Graph;
import org.aerf.model.NodeRef;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Computes {@code C = resolved relevant relations / total extracted
 * relevant relations} (AERF v0.4 section 5.4).
 *
 * <p>An implementation decision: this is a graph-wide measure, not
 * scoped to any one entropy dimension's own relation filter (unlike
 * layer/cycle/persistence entropy, each of which only cares about a
 * configured relation subset). Section 5.4 is written at the level of
 * the overall calibration/risk model, not tied to a specific metric, so
 * "relevant relations" is read here as every edge the extraction process
 * produced: {@code total} is {@code graph.edges().size()}, and
 * {@code resolved} is the count of edges whose source and target are
 * both {@link NodeRef.Resolved} (an unresolved endpoint means extraction
 * observed a relation but could not identify what it points to — exactly
 * the kind of incompleteness section 5.4 asks to be measured and
 * surfaced, not hidden).
 *
 * <p>Per section 5.4: "Confidence must be reported rather than silently
 * converting incomplete evidence into certainty. Low-confidence analyses
 * must be visible to governance consumers." This method never rounds an
 * incomplete graph up to full confidence, and reports undefined (not a
 * misleading 1.0 or 0.0) when there is nothing to measure at all.
 */
public final class AnalysisConfidence {

    private AnalysisConfidence() {
    }

    public static OptionalDouble compute(Graph graph) {
        Objects.requireNonNull(graph, "graph");

        var edges = graph.edges();
        if (edges.isEmpty()) {
            return OptionalDouble.empty();
        }

        long resolved = edges.stream().filter(AnalysisConfidence::bothEndpointsResolved).count();
        return OptionalDouble.of((double) resolved / edges.size());
    }

    private static boolean bothEndpointsResolved(Edge edge) {
        return edge.source() instanceof NodeRef.Resolved && edge.target() instanceof NodeRef.Resolved;
    }
}
