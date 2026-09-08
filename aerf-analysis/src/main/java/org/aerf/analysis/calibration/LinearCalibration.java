package org.aerf.analysis.calibration;

/** {@code f(E) = E} — "Proportional deviation" (AERF v0.4 section 5.2). */
public final class LinearCalibration implements CalibrationFunction {

    @Override
    public double apply(double entropyValue) {
        return entropyValue;
    }

    @Override
    public String name() {
        return "linear";
    }
}
