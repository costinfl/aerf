package org.aerf.pipeline;

import org.aerf.analysis.calibration.DimensionDrift;
import org.aerf.analysis.calibration.Drift;
import org.aerf.analysis.calibration.DriftPenalty;
import org.aerf.analysis.calibration.EntropySnapshot;
import org.aerf.analysis.calibration.Risk;
import org.aerf.analysis.calibration.RiskAssessment;
import org.aerf.analysis.governance.ApprovedExceptions;
import org.aerf.analysis.governance.DriftSensitivity;
import org.aerf.analysis.governance.GovernancePolicy;
import org.aerf.analysis.governance.InvariantWeights;
import org.aerf.analysis.governance.Subsystems;
import org.aerf.analysis.governance.WeightedDriftDimension;
import org.aerf.analysis.metrics.layer.LayerPolicy;
import org.aerf.model.Role;
import org.aerf.report.json.JsonWriter;
import org.aerf.report.RiskJson;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Increment 30 (OQ-14)'s reachability proof, modelled on
 * {@link DriftEndToEndTest}: the full baseline → current → drift → R →
 * JSON chain, with the current side coming from a genuine
 * {@link Pipeline#run}.
 *
 * <p>The baseline is supplied by the caller for the same reason
 * {@code DriftEndToEndTest} gives: production's baseline is loaded from
 * wherever prior measurements are kept, and reaching into storage is a
 * concern this pipeline deliberately does not have. That is also exactly
 * why {@code R} is a pure function rather than a {@code PipelineReport}
 * component.
 */
class RiskEndToEndTest {

    private static final String SUBJECT_ID = "defect-sample";

    @Test
    void aRealRunDiffedAgainstABaselineProducesRiskWithBothTermsVisible() {
        PipelineReport report = Pipeline.run(sensitiveConfig());
        EntropySnapshot current = report.toEntropySnapshot(SUBJECT_ID);
        EntropySnapshot baseline = baselineLike(current, 0.6, 0.0);

        Map<String, DimensionDrift> drift = Drift.compute(baseline, current);
        RiskAssessment risk = Risk.compute(report.totalEntropy(), baseline, current, drift,
                report.governance().driftSensitivity());

        // layer went 0.6 -> 1.0 (worse by 0.4); persistence went 0.0 -> 0.5
        // (worse by 0.5). Both weighted 1.0, beta 2.0.
        assertEquals(report.totalEntropy(), risk.entropyTerm());
        assertEquals(OptionalDouble.of(2.0 * (0.4 + 0.5)), risk.driftTerm());
        assertEquals(OptionalDouble.of(report.totalEntropy().getAsDouble() + 1.8), risk.value());
        assertEquals(List.of("layer", "persistence"),
                risk.penalties().stream().map(DriftPenalty::dimension).toList());
    }

    @Test
    void riskRefusesWhenTheBaselineWasMeasuredUnderADifferentPolicy() {
        // Finding B's fix, end to end. The two runs differ only in their
        // layering matrix, so their entropy difference is a policy change
        // and not code drift - and R says so rather than reporting it.
        PipelineReport strict = Pipeline.run(sensitiveConfig());
        PipelineReport relaxed = Pipeline.run(relaxedSensitiveConfig());

        EntropySnapshot baseline = relaxed.toEntropySnapshot(SUBJECT_ID);
        EntropySnapshot current = strict.toEntropySnapshot(SUBJECT_ID);

        RiskAssessment risk = Risk.compute(strict.totalEntropy(), baseline, current,
                Drift.compute(baseline, current), strict.governance().driftSensitivity());

        assertTrue(risk.value().isEmpty());
        assertTrue(risk.undefinedBecause().stream().anyMatch(r -> r.contains("different policies")),
                risk.undefinedBecause().toString());
    }

    @Test
    void aBaselineWithNoRecordedPolicyIsNotTreatedAsAMatch() {
        PipelineReport report = Pipeline.run(sensitiveConfig());
        EntropySnapshot current = report.toEntropySnapshot(SUBJECT_ID);
        EntropySnapshot legacyBaseline = EntropySnapshot.withoutGovernanceIdentity(
                SUBJECT_ID, current.dimensionValues());

        RiskAssessment risk = Risk.compute(report.totalEntropy(), legacyBaseline, current,
                Drift.compute(legacyBaseline, current), report.governance().driftSensitivity());

        assertTrue(risk.value().isEmpty());
        assertTrue(risk.undefinedBecause().stream().anyMatch(r -> r.contains("unrecorded")),
                risk.undefinedBecause().toString());
    }

    @Test
    void riskReachesJsonWithItsTermsAndPenaltiesSeparate() {
        PipelineReport report = Pipeline.run(sensitiveConfig());
        EntropySnapshot current = report.toEntropySnapshot(SUBJECT_ID);
        EntropySnapshot baseline = baselineLike(current, 0.6, 0.0);

        String json = JsonWriter.write(RiskJson.assessment(Risk.compute(
                report.totalEntropy(), baseline, current, Drift.compute(baseline, current),
                report.governance().driftSensitivity())));

        assertTrue(json.contains("\"entropyTerm\":"), json);
        assertTrue(json.contains("\"driftTerm\":1.8"), json);
        assertTrue(json.contains("\"dimension\":\"layer\""), json);
    }

    @Test
    void riskIsNotAPipelineReportComponent() {
        // The pipeline has no subject identity and no baseline, and
        // deliberately does not reach into storage for one - so R cannot
        // be produced here, and a report field that could never be
        // populated would be worse than its absence.
        assertTrue(java.util.Arrays.stream(PipelineReport.class.getRecordComponents())
                        .noneMatch(c -> c.getType().getSimpleName().contains("Risk")),
                "R is computed by a caller holding a baseline, not by the pipeline");
        assertTrue(!JsonWriter.write(Main.toJson(Pipeline.run(PipelineTest.config()))).contains("\"risk\""),
                "and it appears in no pipeline-produced JSON");
    }

    /** The same dimension values as {@code current}, with two dimensions overridden. */
    private static EntropySnapshot baselineLike(EntropySnapshot current, double layer, double persistence) {
        Map<String, OptionalDouble> values = new LinkedHashMap<>(current.dimensionValues());
        values.put("layer", OptionalDouble.of(layer));
        values.put("persistence", OptionalDouble.of(persistence));
        return new EntropySnapshot(current.subjectId(), values, current.governanceFingerprint());
    }

    private static PipelineConfig sensitiveConfig() {
        return withGovernance(PipelineTest.config(), PipelineTest.config().governance().layerPolicy());
    }

    private static PipelineConfig relaxedSensitiveConfig() {
        return withGovernance(PipelineTest.config(), LayerPolicy.of(
                Set.of(Role.PRESENTATION, Role.APPLICATION, Role.PERSISTENCE),
                Map.of(Role.PRESENTATION, Set.of(Role.PRESENTATION, Role.APPLICATION, Role.PERSISTENCE),
                        Role.APPLICATION, Set.of(Role.APPLICATION, Role.PERSISTENCE),
                        Role.PERSISTENCE, Set.of(Role.PERSISTENCE))));
    }

    private static PipelineConfig withGovernance(PipelineConfig base, LayerPolicy layerPolicy) {
        GovernancePolicy g = base.governance();
        return new PipelineConfig(base.extraction(), base.detection(), new GovernancePolicy(
                layerPolicy, Subsystems.none(), g.includeSelfCyclesInCycleEntropy(),
                g.calibrationProfile(), g.invariants(), InvariantWeights.none(),
                DriftSensitivity.of(2.0, List.of(
                        new WeightedDriftDimension("layer", 1.0),
                        new WeightedDriftDimension("persistence", 1.0))),
                ApprovedExceptions.none()));
    }
}
