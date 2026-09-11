package org.aerf.analysis.calibration;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AERF v0.4 section 5.3: {@code Delta_d = E_d^t - E_d^0}. See {@link
 * Drift}'s own javadoc for why this class exists as a v0.4-contract
 * remediation rather than a v0.4.2 feature (V04-CAL-02 in
 * {@code docs/aerf-v0.4-reconciliation-evidence.md}).
 */
class DriftTest {

    @Test
    void deltaIsCurrentMinusBaselinePerSection53() {
        EntropySnapshot baseline = new EntropySnapshot("spring-petclinic",
                Map.of("layer", OptionalDouble.of(0.20)));
        EntropySnapshot current = new EntropySnapshot("spring-petclinic",
                Map.of("layer", OptionalDouble.of(0.35)));

        Map<String, DimensionDrift> drift = Drift.compute(baseline, current);

        DimensionDrift layerDrift = drift.get("layer");
        assertEquals(0.20, layerDrift.baselineValue());
        assertEquals(0.35, layerDrift.currentValue());
        assertEquals(0.15, layerDrift.delta(), 1e-9);
    }

    @Test
    void negativeDeltaMeansImprovement() {
        EntropySnapshot baseline = new EntropySnapshot("p", Map.of("persistence", OptionalDouble.of(0.5)));
        EntropySnapshot current = new EntropySnapshot("p", Map.of("persistence", OptionalDouble.of(0.1)));

        Map<String, DimensionDrift> drift = Drift.compute(baseline, current);

        assertEquals(-0.4, drift.get("persistence").delta(), 1e-9);
    }

    @Test
    void baselineAndCurrentValuesRemainSeparatelyAvailableAlongsideTheDelta() {
        // Section 14's "measurement before aggregation" principle, applied
        // to drift the same way it already applies to every entropy
        // result: the raw inputs stay visible, not just their difference.
        EntropySnapshot baseline = new EntropySnapshot("p", Map.of("cycle", OptionalDouble.of(0.3)));
        EntropySnapshot current = new EntropySnapshot("p", Map.of("cycle", OptionalDouble.of(0.3)));

        DimensionDrift result = Drift.compute(baseline, current).get("cycle");

        assertEquals(0.3, result.baselineValue());
        assertEquals(0.3, result.currentValue());
        assertEquals(0.0, result.delta());
    }

    @Test
    void aDimensionUndefinedInTheBaselineContributesNoEntry() {
        EntropySnapshot baseline = new EntropySnapshot("p", Map.of("security", OptionalDouble.empty()));
        EntropySnapshot current = new EntropySnapshot("p", Map.of("security", OptionalDouble.of(0.2)));

        Map<String, DimensionDrift> drift = Drift.compute(baseline, current);

        assertTrue(drift.isEmpty(), "security was unmeasurable at baseline, so its drift is unmeasurable too - not zero");
    }

    @Test
    void aDimensionUndefinedInTheCurrentMeasurementContributesNoEntry() {
        EntropySnapshot baseline = new EntropySnapshot("p", Map.of("security", OptionalDouble.of(0.2)));
        EntropySnapshot current = new EntropySnapshot("p", Map.of("security", OptionalDouble.empty()));

        Map<String, DimensionDrift> drift = Drift.compute(baseline, current);

        assertTrue(drift.isEmpty());
    }

    @Test
    void aDimensionMissingFromTheBaselineEntirelyContributesNoEntry() {
        EntropySnapshot baseline = new EntropySnapshot("p", Map.of("layer", OptionalDouble.of(0.1)));
        EntropySnapshot current = new EntropySnapshot("p", Map.of(
                "layer", OptionalDouble.of(0.2),
                "cycle", OptionalDouble.of(0.4)));

        Map<String, DimensionDrift> drift = Drift.compute(baseline, current);

        assertEquals(1, drift.size());
        assertTrue(drift.containsKey("layer"));
    }

    @Test
    void refusesToCompareMeasurementsOfDifferentSubjects() {
        EntropySnapshot baseline = new EntropySnapshot("spring-petclinic", Map.of("layer", OptionalDouble.of(0.1)));
        EntropySnapshot current = new EntropySnapshot("spring-framework-petclinic", Map.of("layer", OptionalDouble.of(0.2)));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Drift.compute(baseline, current));
        assertTrue(exception.getMessage().contains("spring-petclinic"));
        assertTrue(exception.getMessage().contains("spring-framework-petclinic"));
    }

    @Test
    void multipleDimensionsAreComputedIndependently() {
        EntropySnapshot baseline = new EntropySnapshot("p", Map.of(
                "layer", OptionalDouble.of(0.1),
                "cycle", OptionalDouble.of(0.5),
                "persistence", OptionalDouble.of(0.0)));
        EntropySnapshot current = new EntropySnapshot("p", Map.of(
                "layer", OptionalDouble.of(0.3),
                "cycle", OptionalDouble.of(0.5),
                "persistence", OptionalDouble.of(0.25)));

        Map<String, DimensionDrift> drift = Drift.compute(baseline, current);

        assertEquals(3, drift.size());
        assertEquals(0.2, drift.get("layer").delta(), 1e-9);
        assertEquals(0.0, drift.get("cycle").delta(), 1e-9);
        assertEquals(0.25, drift.get("persistence").delta(), 1e-9);
    }
}
