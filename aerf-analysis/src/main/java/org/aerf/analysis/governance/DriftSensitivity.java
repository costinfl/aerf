package org.aerf.analysis.governance;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * How much an organization cares about architecture <em>getting worse</em>
 * — AERF v0.4 §5.3's {@code beta} and {@code gamma_d} together
 * (Increment 30, OQ-14).
 *
 * <p><b>The two are declared together, deliberately.</b> A {@code beta}
 * with no {@code gamma_d}, and {@code gamma_d} with no {@code beta}, are
 * both "no drift penalty" stated two different ways; holding them in one
 * value means an organization cannot half-declare a sensitivity and be
 * surprised by which half won.
 *
 * <p><b>An empty instance leaves {@code R} undefined, not equal to
 * {@code E_total}.</b> An organization that has declared no drift
 * sensitivity has not said drift is harmless — it has said nothing, and
 * reporting {@code E_total} under the name {@code R} would quietly
 * present a different quantity than the one that was asked for.
 *
 * <p>There is deliberately no sum-to-one validation, matching
 * {@link InvariantWeights} rather than {@code CalibrationProfile}: §5.3
 * imposes no normalization on {@code gamma_d}.
 */
public final class DriftSensitivity {

    private static final DriftSensitivity NONE = new DriftSensitivity(OptionalDouble.empty(), List.of());

    private final OptionalDouble beta;
    private final List<WeightedDriftDimension> declared;

    private DriftSensitivity(OptionalDouble beta, List<WeightedDriftDimension> declared) {
        this.beta = Objects.requireNonNull(beta, "beta");
        this.declared = List.copyOf(Objects.requireNonNull(declared, "declared"));
        validateNoDimensionIsWeightedTwice(this.declared);
    }

    public static DriftSensitivity of(double beta, List<WeightedDriftDimension> dimensionWeights) {
        if (beta < 0.0) {
            throw new IllegalArgumentException("beta must not be negative: " + beta);
        }
        return new DriftSensitivity(OptionalDouble.of(beta), dimensionWeights);
    }

    /** No drift sensitivity declared: {@code R} is undefined for this run. */
    public static DriftSensitivity none() {
        return NONE;
    }

    /** §5.3's global drift sensitivity, or empty when none was declared. */
    public OptionalDouble beta() {
        return beta;
    }

    /** In declaration order — the organization's own, never re-sorted. */
    public List<WeightedDriftDimension> declared() {
        return declared;
    }

    public boolean isEmpty() {
        return beta.isEmpty();
    }

    /**
     * This dimension's {@code gamma_d}, or empty when it carries none.
     * Construction guarantees at most one match, so this is
     * order-independent.
     */
    public OptionalDouble weightFor(String dimension) {
        Objects.requireNonNull(dimension, "dimension");
        return declared.stream()
                .filter(weighted -> weighted.dimension().equals(dimension))
                .mapToDouble(WeightedDriftDimension::weight)
                .findFirst();
    }

    private static void validateNoDimensionIsWeightedTwice(List<WeightedDriftDimension> declared) {
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (WeightedDriftDimension weighted : declared) {
            if (!seen.add(weighted.dimension())) {
                duplicates.add(weighted.dimension());
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException(
                    "a dimension may carry only one drift weight, but these are weighted more than once: "
                            + duplicates);
        }
    }
}
