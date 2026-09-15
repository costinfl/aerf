package org.aerf.analysis.calibration;

import java.util.Objects;

/**
 * One invariant's own term in {@code E_inv = sum(lambda_k * I_k(S))}
 * (AERF v0.4 §6.1, Increment 29).
 *
 * <p>Kept alongside the total rather than collapsed into it, so the sum
 * can always be read back to the invariants that produced it. That is
 * what makes OQ-15's "no violation disappears through aggregation"
 * structural rather than argued: a reader sees each λ_k, each
 * {@code I_k}, and each product, next to the number they add up to.
 *
 * @param indicatorValue §6.1's {@code I_k(S)} — 0 when the invariant
 *                       holds, 1 when it is violated
 * @param contribution   {@code weight * indicatorValue}, carried
 *                       explicitly so a reader never has to recompute it
 *                       to check the total
 */
public record InvariantContribution(String invariantName, double weight, int indicatorValue, double contribution) {

    public InvariantContribution {
        Objects.requireNonNull(invariantName, "invariantName");
        if (invariantName.isBlank()) {
            throw new IllegalArgumentException("invariantName must not be blank");
        }
        if (indicatorValue != 0 && indicatorValue != 1) {
            throw new IllegalArgumentException(
                    "I_k(S) is 0 when an invariant holds and 1 when violated; got " + indicatorValue);
        }
    }
}
