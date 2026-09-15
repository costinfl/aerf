package org.aerf.report;

import org.aerf.analysis.calibration.DriftPenalty;
import org.aerf.analysis.calibration.RiskAssessment;
import org.aerf.report.json.JsonWriter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Increment 30 (OQ-14): R serialized with its terms, never as a bare score. */
class RiskJsonTest {

    @Test
    void bothTermsAreSerializedBesideTheTotal() {
        String json = JsonWriter.write(RiskJson.assessment(new RiskAssessment(
                OptionalDouble.of(1.0), OptionalDouble.of(0.4), OptionalDouble.of(0.6),
                List.of(new DriftPenalty("layer", 2.0, 0.3, 0.6)), List.of())));

        assertEquals("{\"value\":1.0,\"entropyTerm\":0.4,\"driftTerm\":0.6,"
                        + "\"penalties\":[{\"dimension\":\"layer\",\"gamma\":2.0,\"delta\":0.3,\"penalty\":0.6}],"
                        + "\"undefinedBecause\":[]}",
                json);
    }

    @Test
    void anImprovedDimensionShowsANegativeDeltaAndAZeroPenalty() {
        // Both are emitted so a reader can see that a dimension improved
        // and that its improvement contributed nothing.
        String json = JsonWriter.write(RiskJson.assessment(new RiskAssessment(
                OptionalDouble.of(0.4), OptionalDouble.of(0.4), OptionalDouble.of(0.0),
                List.of(new DriftPenalty("layer", 1.0, -0.4, 0.0)), List.of())));

        assertTrue(json.contains("\"delta\":-0.4,\"penalty\":0.0"), json);
    }

    @Test
    void anUndefinedRiskSerializesAsNullWithItsReasons() {
        String json = JsonWriter.write(RiskJson.assessment(new RiskAssessment(
                OptionalDouble.empty(), OptionalDouble.of(0.4), OptionalDouble.empty(),
                List.of(), List.of("the two measurements were governed by different policies"))));

        assertTrue(json.contains("\"value\":null"), json);
        assertTrue(json.contains("\"entropyTerm\":0.4"),
                "the term that was computable stays visible: " + json);
        assertTrue(json.contains("different policies"), json);
    }
}
