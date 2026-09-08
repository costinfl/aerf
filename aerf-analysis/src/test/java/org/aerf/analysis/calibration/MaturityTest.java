package org.aerf.analysis.calibration;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaturityTest {

    @Test
    void isOneMinusTotalEntropy() {
        assertEquals(OptionalDouble.of(0.7), Maturity.compute(OptionalDouble.of(0.3)));
    }

    @Test
    void isUndefinedWhenTotalEntropyIsUndefined() {
        assertTrue(Maturity.compute(OptionalDouble.empty()).isEmpty());
    }

    @Test
    void classifiesBelowFortyAsChaotic() {
        assertEquals(MaturityLevel.L0_CHAOTIC, MaturityLevel.classify(0.39));
    }

    @Test
    void classifiesTheInteriorBoundariesAsLowerInclusive() {
        assertEquals(MaturityLevel.L1_REACTIVE, MaturityLevel.classify(0.40));
        assertEquals(MaturityLevel.L2_STRUCTURED, MaturityLevel.classify(0.60));
        assertEquals(MaturityLevel.L3_CONTROLLED, MaturityLevel.classify(0.75));
    }

    @Test
    void classifiesExactlyNinetyAsControlledNotOptimized() {
        // Section 5.5 states "L4: M > 0.90" as a strict inequality, so 0.90
        // itself belongs to L3, not L4 - unlike the interior boundaries,
        // this one is unambiguous in the source text.
        assertEquals(MaturityLevel.L3_CONTROLLED, MaturityLevel.classify(0.90));
    }

    @Test
    void classifiesAboveNinetyAsOptimized() {
        assertEquals(MaturityLevel.L4_OPTIMIZED, MaturityLevel.classify(0.91));
    }
}
