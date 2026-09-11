package org.aerf.analysis.calibration;

import java.util.Objects;

/**
 * One entropy dimension's baseline-relative drift, {@code Delta_d = E_d^t
 * - E_d^0} (AERF v0.4 section 5.3). {@code baselineValue} and {@code
 * currentValue} are kept alongside {@code delta}, not collapsed away, so
 * both remain separately visible - the same "measurement before
 * aggregation" principle this project already applies to every entropy
 * result (e.g. {@code LayerEntropyResult} keeps the edges, not just the
 * ratio) and to confidence (section 5.4) staying separate from role
 * outcome (AERF v0.4.1 Amendment 7).
 */
public record DimensionDrift(String dimension, double baselineValue, double currentValue, double delta) {

    public DimensionDrift {
        Objects.requireNonNull(dimension, "dimension");
        if (dimension.isBlank()) {
            throw new IllegalArgumentException("dimension must not be blank");
        }
    }
}
