package org.aerf.analysis.governance;

import java.util.Objects;

/**
 * One invariant's importance to an organization — AERF v0.4 §6.1's
 * {@code lambda_k} in {@code E_inv = sum(lambda_k * I_k(S))}
 * (Increment 29, OQ-15).
 *
 * <p><b>The weight lives beside the invariant, not inside it.</b>
 * {@code Invariant} is §6.3's rule — its scope, condition and assertion —
 * and stays untouched. λ_k is a governance choice <em>about</em> a rule
 * rather than a property <em>of</em> one, which is exactly the shape the
 * entropy side already established: a dimension's weight lives in
 * {@link WeightedDimension}, never on the dimension.
 *
 * <p><b>Why not derive λ_k from {@code severity}.</b> {@code Invariant}'s
 * own documentation says severity is deliberately free text because
 * "inventing a closed set here would assert a taxonomy the specification
 * does not". A severity-to-weight table would assert precisely that
 * taxonomy through the back door. The two stay independent: an
 * organization may weight a "minor" invariant heavily, and nothing here
 * stops it.
 *
 * <p><b>No upper bound and no sum constraint</b>, unlike
 * {@link org.aerf.analysis.calibration.CalibrationProfile}'s
 * {@code sum(w_d) = 1}. §6.1 states no normalization constraint on λ_k,
 * and inventing one would assert something the specification declines to.
 * The consequence is deliberate and recorded: {@code E_inv} is unbounded,
 * and therefore is not an entropy dimension — see
 * {@link org.aerf.analysis.calibration.AggregatedInvariants}.
 *
 * @param invariantName the {@code Invariant#name()} this weight applies to —
 *                      the same name-based join {@code WeightedDimension} uses
 *                      against the dimension-value map
 */
public record WeightedInvariant(String invariantName, double weight) {

    public WeightedInvariant {
        Objects.requireNonNull(invariantName, "invariantName");
        if (invariantName.isBlank()) {
            throw new IllegalArgumentException("invariantName must not be blank");
        }
        if (weight < 0.0) {
            throw new IllegalArgumentException("weight must not be negative: " + weight);
        }
    }
}
