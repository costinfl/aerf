package org.aerf.analysis.calibration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CalibrationProfileTest {

    @Test
    void weightsSummingToOneAreAccepted() {
        CalibrationProfile profile = CalibrationProfile.of(List.of(
                new WeightedDimension("layer", 0.4, new LinearCalibration()),
                new WeightedDimension("cycle", 0.6, new LinearCalibration())));

        assertEquals(2, profile.dimensions().size());
    }

    @Test
    void weightsNotSummingToOneAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> CalibrationProfile.of(List.of(
                new WeightedDimension("layer", 0.4, new LinearCalibration()),
                new WeightedDimension("cycle", 0.5, new LinearCalibration()))));
    }

    @Test
    void aZeroWeightedDimensionIsAllowedAsLongAsTheTotalIsOne() {
        CalibrationProfile profile = CalibrationProfile.of(List.of(
                new WeightedDimension("layer", 1.0, new LinearCalibration()),
                new WeightedDimension("cycle", 0.0, new LinearCalibration())));

        assertEquals(2, profile.dimensions().size());
    }

    @Test
    void aNegativeWeightIsRejectedAtTheDimensionItself() {
        assertThrows(IllegalArgumentException.class, () -> new WeightedDimension("layer", -0.1, new LinearCalibration()));
    }
}
