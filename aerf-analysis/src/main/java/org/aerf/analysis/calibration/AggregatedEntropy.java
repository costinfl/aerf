package org.aerf.analysis.calibration;

import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Computes {@code E_total = sum(w_d * f_d(E_d))} (AERF v0.4 section 5.1)
 * from a {@link CalibrationProfile} and a map of each dimension's raw
 * entropy value.
 *
 * <p><b>Undefined-dimension policy (an implementation decision, not
 * dictated by v0.4):</b> every entropy calculator built so far
 * ({@code LayerEntropyResult}, {@code CycleEntropyResult}, etc.) can
 * legitimately report no value at all when a graph has no relevant
 * contexts for that dimension — undefined, not zero, by design (see
 * those calculators' own documentation). Section 5.1's formula assumes
 * every weighted dimension contributes a value, without saying what to
 * do when one doesn't. Silently treating an undefined dimension as
 * {@code 0.0} would claim "no deviation detected" where actually nothing
 * was measurable, exactly the conflation those calculators were built to
 * avoid. This method instead requires a defined value for every
 * dimension carrying nonzero weight; if any such dimension is undefined,
 * {@code E_total} is undefined too. A dimension explicitly weighted
 * {@code 0} is exempt, since it does not actually contribute to the sum.
 */
public final class AggregatedEntropy {

    private AggregatedEntropy() {
    }

    public static OptionalDouble compute(CalibrationProfile profile, Map<String, OptionalDouble> dimensionValues) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(dimensionValues, "dimensionValues");

        double total = 0.0;
        for (WeightedDimension dimension : profile.dimensions()) {
            if (dimension.weight() == 0.0) {
                continue;
            }
            OptionalDouble value = dimensionValues.get(dimension.name());
            if (value == null || value.isEmpty()) {
                return OptionalDouble.empty();
            }
            total += dimension.weight() * dimension.calibration().apply(value.getAsDouble());
        }
        return OptionalDouble.of(total);
    }
}
