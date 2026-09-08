package org.aerf.analysis.calibration;

/**
 * {@code f(E) = 1 / (1 + exp(-k(E-t)))} — "Sharp escalation after
 * tolerance" (AERF v0.4 section 5.2).
 *
 * @param k steepness of escalation; governance-selected, no default asserted.
 * @param t the tolerance threshold around which escalation is centered; governance-selected.
 */
public record SigmoidCalibration(double k, double t) implements CalibrationFunction {

    @Override
    public double apply(double entropyValue) {
        return 1.0 / (1.0 + Math.exp(-k * (entropyValue - t)));
    }

    @Override
    public String name() {
        return "sigmoid";
    }
}
