package org.aerf.report;

import org.aerf.analysis.calibration.MaturityLevel;
import org.aerf.report.json.JsonWriter;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalibrationJsonTest {

    @Test
    void aDefinedTotalEntropySerializesAsANumber() {
        assertEquals("0.5", JsonWriter.write(CalibrationJson.totalEntropy(OptionalDouble.of(0.5))));
    }

    @Test
    void anUndefinedTotalEntropySerializesAsNullNotZero() {
        assertEquals("null", JsonWriter.write(CalibrationJson.totalEntropy(OptionalDouble.empty())));
    }

    @Test
    void maturityLevelSerializesAsItsName() {
        assertEquals("\"L1_REACTIVE\"", JsonWriter.write(CalibrationJson.maturityLevel(MaturityLevel.L1_REACTIVE)));
    }

    @Test
    void perDimensionConfidenceKeepsUndefinedDistinctFromZero() {
        // OQ-13: security has no resolved/unresolved population to measure
        // at all, while a real 0.0 means every reference that dimension
        // measured failed to resolve. Collapsing the first onto the second
        // would be exactly the coercion section 5.4 forbids.
        Map<String, OptionalDouble> byDimension = new LinkedHashMap<>();
        byDimension.put("layer", OptionalDouble.of(0.6));
        byDimension.put("cycle", OptionalDouble.of(0.0));
        byDimension.put("security", OptionalDouble.empty());

        assertEquals("{\"layer\":0.6,\"cycle\":0.0,\"security\":null}",
                JsonWriter.write(CalibrationJson.confidenceByDimension(byDimension)));
    }

    @Test
    void perDimensionConfidencePreservesTheOrderItWasGiven() {
        // The map arrives from Pipeline in dimension order; Map.copyOf
        // would not preserve it (the Increment 1 gotcha), so this pins
        // that the writer itself does not reshuffle either.
        Map<String, OptionalDouble> byDimension = new LinkedHashMap<>();
        byDimension.put("persistence", OptionalDouble.of(1.0));
        byDimension.put("cycle", OptionalDouble.of(0.5));
        byDimension.put("layer", OptionalDouble.of(0.25));

        assertEquals("{\"persistence\":1.0,\"cycle\":0.5,\"layer\":0.25}",
                JsonWriter.write(CalibrationJson.confidenceByDimension(byDimension)));
    }

    @Test
    void noDimensionsAtAllIsAnEmptyObjectNotNull() {
        assertEquals("{}", JsonWriter.write(CalibrationJson.confidenceByDimension(Map.of())));
    }
}
