package org.aerf.analysis.calibration;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregatedEntropyTest {

    @Test
    void computesTheWeightedSumWithLinearCalibration() {
        CalibrationProfile profile = CalibrationProfile.of(List.of(
                new WeightedDimension("layer", 0.5, new LinearCalibration()),
                new WeightedDimension("cycle", 0.5, new LinearCalibration())));
        Map<String, OptionalDouble> values = Map.of(
                "layer", OptionalDouble.of(0.4),
                "cycle", OptionalDouble.of(0.6));

        OptionalDouble total = AggregatedEntropy.compute(profile, values);

        assertEquals(OptionalDouble.of(0.5), total);
    }

    @Test
    void appliesEachDimensionsOwnCalibrationFunction() {
        CalibrationProfile profile = CalibrationProfile.of(List.of(
                new WeightedDimension("layer", 1.0, new PowerCalibration(2.0))));
        Map<String, OptionalDouble> values = Map.of("layer", OptionalDouble.of(0.5));

        OptionalDouble total = AggregatedEntropy.compute(profile, values);

        assertEquals(OptionalDouble.of(0.25), total);
    }

    @Test
    void anUndefinedWeightedDimensionMakesTheAggregateUndefined() {
        CalibrationProfile profile = CalibrationProfile.of(List.of(
                new WeightedDimension("layer", 0.5, new LinearCalibration()),
                new WeightedDimension("security", 0.5, new LinearCalibration())));
        Map<String, OptionalDouble> values = Map.of(
                "layer", OptionalDouble.of(0.4),
                "security", OptionalDouble.empty());

        OptionalDouble total = AggregatedEntropy.compute(profile, values);

        assertTrue(total.isEmpty(), "security has no measurable opportunities, so the aggregate cannot claim certainty");
    }

    @Test
    void aZeroWeightedDimensionBeingUndefinedDoesNotBlockAggregation() {
        CalibrationProfile profile = CalibrationProfile.of(List.of(
                new WeightedDimension("layer", 1.0, new LinearCalibration()),
                new WeightedDimension("security", 0.0, new LinearCalibration())));
        Map<String, OptionalDouble> values = Map.of(
                "layer", OptionalDouble.of(0.4),
                "security", OptionalDouble.empty());

        OptionalDouble total = AggregatedEntropy.compute(profile, values);

        assertEquals(OptionalDouble.of(0.4), total, "security contributes nothing at weight 0, so its being undefined is irrelevant");
    }

    @Test
    void aMissingKeyInTheValueMapIsTreatedTheSameAsUndefined() {
        CalibrationProfile profile = CalibrationProfile.of(List.of(
                new WeightedDimension("layer", 1.0, new LinearCalibration())));

        OptionalDouble total = AggregatedEntropy.compute(profile, Map.of());

        assertTrue(total.isEmpty());
    }
}
