package org.aerf.analysis.governance;

import java.util.Objects;

/**
 * One entropy dimension's drift weight — AERF v0.4 §5.3's
 * {@code gamma_d} in
 * {@code R = sum(w_d * f_d(E_d)) + beta * sum(gamma_d * max(0, Delta_d))}
 * (Increment 30, OQ-14).
 *
 * <p>Distinct from {@link org.aerf.analysis.calibration.WeightedDimension}'s
 * {@code w_d}, which weights a dimension's <em>level</em>. This weights
 * how much that dimension <em>getting worse</em> matters, which is a
 * separate governance judgement: an organization may tolerate a high but
 * stable cycle entropy while treating any increase in it as serious.
 *
 * <p><b>No upper bound and no sum constraint</b>, for the same reason
 * {@link WeightedInvariant} has none: §5.3 states no normalization
 * constraint on {@code gamma_d}, just as §6.1 states none on
 * {@code lambda_k}. §5.1's {@code sum(w_d) = 1} is the exception, not the
 * rule. The consequence is that the drift term is unbounded, so {@code R}
 * is not an entropy dimension either — see
 * {@link org.aerf.analysis.calibration.Risk}.
 *
 * @param dimension the entropy dimension name this weight applies to —
 *                  the same four keys ({@code layer}, {@code cycle},
 *                  {@code persistence}, {@code security}) that
 *                  {@code WeightedDimension} and {@code EntropySnapshot}
 *                  already join on
 */
public record WeightedDriftDimension(String dimension, double weight) {

    public WeightedDriftDimension {
        Objects.requireNonNull(dimension, "dimension");
        if (dimension.isBlank()) {
            throw new IllegalArgumentException("dimension must not be blank");
        }
        if (weight < 0.0) {
            throw new IllegalArgumentException("weight must not be negative: " + weight);
        }
    }
}
