package org.aerf.analysis.view;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * One entropy dimension as the governance view reports it (Increment 31,
 * OQ-16): its value, how much of its evidence was resolved, and the two
 * counts that produced the ratio.
 *
 * <p><b>The counts are not decoration.</b> Every §4 dimension is
 * {@code findings / relevant}, and carrying both means a reader can always
 * reconstruct the number rather than trusting it — 0/0 undefined stays
 * visibly different from 0/21 measured-and-clean, which a bare value could
 * not express. This is the same "measurement before aggregation" stance
 * each result type already takes by keeping its edges and SCCs beside its
 * ratio.
 *
 * @param dimension     one of the four §4 names: layer, cycle, persistence, security
 * @param value         the dimension's own {@code value()}, undefined where it was unmeasurable
 * @param confidence    the per-dimension confidence from Increment 24 (OQ-13);
 *                      security's is deliberately undefined
 * @param relevantCount the denominator: relevant edges, total nodes, or security opportunities
 * @param findingCount  the numerator: violating edges, nodes in a cycle, flagged edges or findings
 */
public record DimensionObservation(
        String dimension,
        OptionalDouble value,
        OptionalDouble confidence,
        int relevantCount,
        int findingCount) {

    public DimensionObservation {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(confidence, "confidence");
        if (dimension.isBlank()) {
            throw new IllegalArgumentException("dimension must not be blank");
        }
        if (relevantCount < 0 || findingCount < 0) {
            throw new IllegalArgumentException(
                    "counts cannot be negative; got relevant=" + relevantCount + " findings=" + findingCount);
        }
        if (findingCount > relevantCount) {
            throw new IllegalArgumentException(
                    "a dimension cannot have more findings than relevant subjects; got "
                            + findingCount + " of " + relevantCount + " for '" + dimension + "'");
        }
    }
}
