package org.aerf.analysis.calibration;

import org.aerf.model.Edge;
import org.aerf.model.Graph;
import org.aerf.model.NodeRef;
import org.aerf.model.RelationType;

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
 * produced <b>except {@link RelationType#MEMBER_OF}</b> (see below):
 * {@code total} is {@code graph.edges().size()} minus the
 * {@code MEMBER_OF} count, and {@code resolved} is the count of the
 * remaining edges whose source and target are both
 * {@link NodeRef.Resolved} (an unresolved endpoint means extraction
 * observed a relation but could not identify what it points to — exactly
 * the kind of incompleteness section 5.4 asks to be measured and
 * surfaced, not hidden).
 *
 * <p><b>{@code MEMBER_OF} exclusion (AERF v0.4.1 patch Amendment 6):</b>
 * unlike every other relation an adapter emits, a {@code MEMBER_OF}
 * edge's target (a method's declaring class) is, by construction, always
 * resolvable — there is no genuine "could this be identified?" question
 * for it the way there is for a call to a possibly-external method or a
 * reference to a possibly-external type. Counting it here would inflate
 * confidence by a fixed amount proportional to how many methods a
 * codebase happens to have, with zero relationship to how well
 * extraction actually resolved anything uncertain — the opposite of what
 * section 5.4 asks confidence to measure. Confirmed directly: adding
 * {@code MEMBER_OF} edges to `aerf-pipeline`'s own defect-sample fixture
 * moved its confidence from 0.714 to 0.857 with no change whatsoever to
 * how well any real, uncertain reference resolved.
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

        var edges = graph.edges().stream().filter(e -> e.relation() != RelationType.MEMBER_OF).toList();
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
