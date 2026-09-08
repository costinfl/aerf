package org.aerf.analysis.calibration;

/**
 * The maturity levels from AERF v0.4 section 5.5. The section's own text
 * is explicit that "these boundaries are provisional and must not be
 * presented as universal benchmarks before validation" — reproduced here
 * unmodified for exactly that reason: to be tested, not to be trusted as
 * settled.
 *
 * <p>Two boundaries are given with an explicit, unambiguous operator:
 * "L0: M &lt; 0.40" and "L4: M &gt; 0.90" are both strict. Those are
 * honored exactly — in particular, {@code maturity == 0.90} is
 * <b>not</b> L4, it falls to L3. The three interior boundaries (0.40,
 * 0.60, 0.75) are each given only as the shared endpoint of two
 * hyphenated ranges (e.g. L1 "0.40-0.60" and L2 "0.60-0.75" both mention
 * 0.60), which does not by itself say which level owns the boundary.
 * This implementation resolves those as lower-inclusive — a specific,
 * documented tie-break the original table does not itself make, adopted
 * here as an implementation decision.
 */
public enum MaturityLevel {
    L0_CHAOTIC,
    L1_REACTIVE,
    L2_STRUCTURED,
    L3_CONTROLLED,
    L4_OPTIMIZED;

    public static MaturityLevel classify(double maturity) {
        if (maturity < 0.40) {
            return L0_CHAOTIC;
        }
        if (maturity < 0.60) {
            return L1_REACTIVE;
        }
        if (maturity < 0.75) {
            return L2_STRUCTURED;
        }
        if (maturity <= 0.90) {
            return L3_CONTROLLED;
        }
        return L4_OPTIMIZED;
    }
}
