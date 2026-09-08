package org.aerf.analysis.calibration;

import java.util.OptionalDouble;

/** {@code M = 1 - E_total} (AERF v0.4 section 5.5). */
public final class Maturity {

    private Maturity() {
    }

    /** Undefined when {@code totalEntropy} is undefined — maturity cannot be more certain than the aggregate it derives from. */
    public static OptionalDouble compute(OptionalDouble totalEntropy) {
        return totalEntropy.isPresent() ? OptionalDouble.of(1.0 - totalEntropy.getAsDouble()) : OptionalDouble.empty();
    }
}
