package org.aerf.analysis.calibration;

import java.util.List;
import java.util.Objects;

/**
 * A validated governance configuration for aggregation (AERF v0.4
 * section 5.1): a set of {@link WeightedDimension}s whose weights sum to
 * 1, per {@code E_total = sum(w_d * f_d(E_d)), with sum(w_d) = 1}. The
 * sum is enforced at construction so an invalid configuration cannot be
 * used to compute a misleading aggregate.
 */
public final class CalibrationProfile {

    private static final double SUM_TOLERANCE = 1e-9;

    private final List<WeightedDimension> dimensions;

    private CalibrationProfile(List<WeightedDimension> dimensions) {
        this.dimensions = List.copyOf(Objects.requireNonNull(dimensions, "dimensions"));
        double sum = this.dimensions.stream().mapToDouble(WeightedDimension::weight).sum();
        if (Math.abs(sum - 1.0) > SUM_TOLERANCE) {
            throw new IllegalArgumentException(
                    "dimension weights must sum to 1.0 (AERF v0.4 section 5.1); got " + sum);
        }
    }

    public static CalibrationProfile of(List<WeightedDimension> dimensions) {
        return new CalibrationProfile(dimensions);
    }

    public List<WeightedDimension> dimensions() {
        return dimensions;
    }
}
