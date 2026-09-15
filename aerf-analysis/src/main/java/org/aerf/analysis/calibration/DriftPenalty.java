package org.aerf.analysis.calibration;

import java.util.Objects;

/**
 * One dimension's own term in §5.3's drift penalty,
 * {@code gamma_d * max(0, Delta_d)} (Increment 30, OQ-14).
 *
 * <p>Kept beside the total so the penalty can always be read back to the
 * dimensions that produced it. {@code delta} is carried unclamped — the
 * raw {@code Delta_d}, which may be negative — while {@code penalty} is
 * the clamped product, so a reader can see both that a dimension improved
 * and that its improvement contributed nothing.
 */
public record DriftPenalty(String dimension, double gamma, double delta, double penalty) {

    public DriftPenalty {
        Objects.requireNonNull(dimension, "dimension");
        if (dimension.isBlank()) {
            throw new IllegalArgumentException("dimension must not be blank");
        }
        if (penalty < 0.0) {
            throw new IllegalArgumentException(
                    "a penalty is gamma * max(0, delta) and can never be negative; got " + penalty);
        }
    }
}
