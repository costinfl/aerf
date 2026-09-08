package org.aerf.analysis.calibration;

/**
 * {@code f(E) = E^alpha} — "Amplification of accumulated deviation"
 * (AERF v0.4 section 5.2).
 *
 * @param alpha exponent; governance-selected, no default asserted.
 */
public record PowerCalibration(double alpha) implements CalibrationFunction {

    @Override
    public double apply(double entropyValue) {
        return Math.pow(entropyValue, alpha);
    }

    @Override
    public String name() {
        return "power";
    }
}
