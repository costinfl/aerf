package org.aerf.analysis.calibration;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * {@code E_inv = sum(lambda_k * I_k(S))} (AERF v0.4 §6.1) together with
 * every term that produced it (Increment 29, OQ-15).
 *
 * <p>{@link #value()} is undefined — never {@code 0.0} — when this run
 * could not compute the sum: either no importance was declared at all, or
 * an invariant carrying a nonzero λ_k was never evaluated.
 * {@link #unevaluatedWeightedInvariants()} names the second case, so an
 * undefined aggregate always carries its own reason rather than leaving a
 * reader to guess.
 *
 * <p><b>This is not an entropy dimension and must never be used as one.</b>
 * §6.1 states no normalization constraint on λ_k, so the sum is unbounded,
 * while §5.1's aggregation assumes every {@code E_d} is in [0,1] and
 * {@link CalibrationProfile} enforces {@code sum(w_d) = 1}. Feeding
 * {@code E_inv} into that aggregation would silently break both
 * assumptions — and would also collapse governance violations into the
 * single architecture score this project has consistently refused to
 * produce. Entropy, drift and violations stay separately visible.
 */
public record InvariantAggregate(
        OptionalDouble value,
        List<InvariantContribution> contributions,
        List<String> unevaluatedWeightedInvariants) {

    private static final InvariantAggregate UNDECLARED =
            new InvariantAggregate(OptionalDouble.empty(), List.of(), List.of());

    public InvariantAggregate {
        Objects.requireNonNull(value, "value");
        contributions = List.copyOf(Objects.requireNonNull(contributions, "contributions"));
        unevaluatedWeightedInvariants =
                List.copyOf(Objects.requireNonNull(unevaluatedWeightedInvariants, "unevaluatedWeightedInvariants"));
    }

    /**
     * No importance was declared, so there is nothing to sum. Undefined
     * rather than zero: declaring no weights is not a statement that an
     * organization's invariants do not matter.
     */
    public static InvariantAggregate undeclared() {
        return UNDECLARED;
    }
}
