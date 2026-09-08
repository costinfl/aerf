package org.aerf.analysis.calibration;

import java.util.Objects;

/**
 * One entropy dimension's contribution to aggregation (AERF v0.4 section
 * 5.1): its name (matching a key in the dimension-value map passed to
 * {@link AggregatedEntropy}), its governance-selected weight {@code w_d},
 * and its calibration function {@code f_d}.
 */
public record WeightedDimension(String name, double weight, CalibrationFunction calibration) {

    public WeightedDimension {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(calibration, "calibration");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (weight < 0.0) {
            throw new IllegalArgumentException("weight must not be negative: " + weight);
        }
    }
}
