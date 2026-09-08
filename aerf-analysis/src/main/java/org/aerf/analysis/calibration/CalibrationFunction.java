package org.aerf.analysis.calibration;

/**
 * A calibration function {@code f_d}, transforming a raw entropy value
 * into a governance-selected risk contribution (AERF v0.4 section 5.1).
 *
 * <p>Section 5.2 names three model families (linear, threshold/sigmoid,
 * power) as candidates, explicitly captioned "model families, not
 * validated universal constants." Appendix E repeats this: "weights,
 * thresholds... calibration functions... must be treated as hypotheses
 * to be tested." No implementation of this interface should be selected
 * or parameterized as a default; every use must be an explicit
 * governance choice.
 */
public interface CalibrationFunction {

    /** @param entropyValue expected in [0, 1], per section 4's E_d in [0, 1]. */
    double apply(double entropyValue);

    String name();
}
