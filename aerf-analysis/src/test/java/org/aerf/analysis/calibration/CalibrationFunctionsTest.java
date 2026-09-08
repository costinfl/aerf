package org.aerf.analysis.calibration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalibrationFunctionsTest {

    @Test
    void linearIsTheIdentity() {
        LinearCalibration linear = new LinearCalibration();

        assertEquals(0.0, linear.apply(0.0), 1e-9);
        assertEquals(0.37, linear.apply(0.37), 1e-9);
        assertEquals(1.0, linear.apply(1.0), 1e-9);
    }

    @Test
    void sigmoidIsExactlyOneHalfAtItsThreshold() {
        SigmoidCalibration sigmoid = new SigmoidCalibration(10.0, 0.35);

        assertEquals(0.5, sigmoid.apply(0.35), 1e-9);
    }

    @Test
    void sigmoidEscalatesSharplyPastItsThreshold() {
        SigmoidCalibration sigmoid = new SigmoidCalibration(60.0, 0.35);

        double belowThreshold = sigmoid.apply(0.30);
        double atThreshold = sigmoid.apply(0.35);
        double aboveThreshold = sigmoid.apply(0.40);

        assertEquals(0.5, atThreshold, 1e-9);
        assertTrue(belowThreshold < 0.1, "well below tolerance should be near-zero");
        assertTrue(aboveThreshold > 0.9, "well above tolerance should be near-one");
    }

    @Test
    void powerAmplifiesAboveOneAndDampensBelowOne() {
        PowerCalibration squared = new PowerCalibration(2.0);
        PowerCalibration squareRoot = new PowerCalibration(0.5);

        assertEquals(0.25, squared.apply(0.5), 1e-9);
        assertEquals(Math.sqrt(0.5), squareRoot.apply(0.5), 1e-9);
    }
}
