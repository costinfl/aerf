package org.aerf.pipeline;

import org.aerf.analysis.calibration.DimensionDrift;
import org.aerf.analysis.calibration.Drift;
import org.aerf.analysis.calibration.EntropySnapshot;
import org.aerf.report.DriftJson;
import org.aerf.report.json.JsonWriter;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The V04-CAL-02 remediation's actual end-to-end proof: a real {@link
 * PipelineReport} (not a hand-built value) converted to an {@link
 * EntropySnapshot}, diffed against a baseline with {@link Drift}, and
 * serialized with {@link DriftJson} — the full "baseline → current →
 * drift → report" chain the AERF v0.4/v0.4.1 contract reconciliation's
 * follow-up review asked to trace.
 *
 * <p><b>Why the baseline side is not also a live {@code Pipeline.run}
 * call:</b> this reactor has exactly one pipeline test fixture
 * ({@code defect-sample}); running the same source twice would only ever
 * produce a zero-drift baseline, proving the wiring compiles but nothing
 * about non-zero deltas flowing through correctly. Constructing the
 * baseline as an {@code EntropySnapshot} directly is also the
 * architecturally honest choice, not a shortcut: production's real
 * baseline is loaded from wherever a caller already keeps prior
 * measurements (the dashboard's Supabase {@code scans} table, per
 * Increment 19) — reading that back is a storage-layer concern this
 * pipeline deliberately does not reach into (see {@link Drift}'s own
 * javadoc), so a test exercising this seam correctly supplies the
 * baseline the same way any real caller would: already in hand as an
 * {@code EntropySnapshot}, however it was obtained. The <em>current</em>
 * side is what this pipeline is actually responsible for producing, so
 * that is the side this test requires to come from a genuine {@link
 * Pipeline#run}.
 */
class DriftEndToEndTest {

    private static final String SUBJECT_ID = "defect-sample";

    @Test
    void aRealPipelineReportProducesNonZeroDriftAgainstAPriorBaselineAndSerializesToJson() {
        PipelineReport currentReport = Pipeline.run(PipelineTest.config());
        EntropySnapshot current = currentReport.toEntropySnapshot(SUBJECT_ID);

        // A plausible prior measurement of the same subject: better on
        // layering, worse on persistence, unchanged on cycles (whatever
        // this sample's own cycle value is), still unmeasurable on
        // security (this sample has no VIEW evidence at either point in
        // time, exactly like the real current scan).
        Map<String, OptionalDouble> baselineValues = new LinkedHashMap<>();
        baselineValues.put("layer", OptionalDouble.of(0.60));
        baselineValues.put("cycle", currentReport.cycleEntropy().value());
        baselineValues.put("persistence", OptionalDouble.of(0.0));
        baselineValues.put("security", OptionalDouble.empty());
        EntropySnapshot baseline = new EntropySnapshot(SUBJECT_ID, baselineValues);

        Map<String, DimensionDrift> drift = Drift.compute(baseline, current);

        // layer: this sample's real, live-computed value is 1.0 (every
        // layer-relevant edge violates, per PipelineTest); baseline was
        // 0.60, so drift is a genuine, real regression.
        assertEquals(OptionalDouble.of(1.0), currentReport.layerEntropy().value());
        DimensionDrift layerDrift = drift.get("layer");
        assertEquals(0.60, layerDrift.baselineValue());
        assertEquals(1.0, layerDrift.currentValue());
        assertEquals(0.40, layerDrift.delta(), 1e-9);

        // persistence: real current value is 0.5 (PipelineTest.findsTheNPlusOneLoop);
        // baseline was 0.0, so this too is a genuine improvement-to-worse drift.
        DimensionDrift persistenceDrift = drift.get("persistence");
        assertEquals(0.0, persistenceDrift.baselineValue());
        assertEquals(0.5, persistenceDrift.currentValue());
        assertEquals(0.5, persistenceDrift.delta(), 1e-9);

        // security: undefined at both points in time, so it must not
        // appear in the drift result at all - not a false zero.
        assertTrue(currentReport.securityEntropy().value().isEmpty());
        assertTrue(!drift.containsKey("security"), "security is unmeasurable at both points in time, not unchanged");

        // → report: the computed drift, not a hand-built one, round-trips
        // through the same JSON infrastructure every other metric uses.
        String json = JsonWriter.write(DriftJson.drift(drift));
        assertTrue(json.contains("\"layer\":{\"baselineValue\":0.6,\"currentValue\":1.0,\"delta\":0.4"),
                () -> "expected the real layer drift in the serialized report, got: " + json);
        assertTrue(json.contains("\"persistence\":{\"baselineValue\":0.0,\"currentValue\":0.5,\"delta\":0.5"),
                () -> "expected the real persistence drift in the serialized report, got: " + json);
        assertTrue(!json.contains("security"), "an undefined-at-both-points dimension must not reach the report");
    }
}
