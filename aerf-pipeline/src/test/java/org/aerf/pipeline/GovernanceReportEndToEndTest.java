package org.aerf.pipeline;

import org.aerf.analysis.calibration.EntropySnapshot;
import org.aerf.analysis.governance.GovernancePolicy;
import org.aerf.analysis.governance.Subsystems;
import org.aerf.analysis.governance.Subsystem;
import org.aerf.analysis.metrics.layer.LayerPolicy;
import org.aerf.model.Role;
import org.aerf.report.json.JsonWriter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Increment 25 (OQ-02)'s reachability proof, modelled on {@link
 * DriftEndToEndTest}: the governance a run was measured under has to
 * arrive in the serialized report through a real {@link Pipeline#run},
 * not merely be unit-testable in isolation.
 *
 * <p>This is the standard the V04-CAL-02 correction established — a
 * formula that was implemented and unit-tested but unreachable from any
 * actual pipeline run had been wrongly reported complete. Increment 24
 * checked its own equivalent by hand at the CLI; this checks it in the
 * suite, so it keeps being checked.
 */
class GovernanceReportEndToEndTest {

    @Test
    void aRealPipelineRunSerializesTheGovernanceItWasRunUnderAlongsideItsMeasurements() {
        PipelineReport report = Pipeline.run(PipelineTest.config());

        String json = JsonWriter.write(Main.toJson(report));

        // The declaration PipelineTest.config() actually made, read back
        // out of the serialized report.
        assertTrue(json.contains("\"governance\":{\"layerPolicy\":{\"knownRoles\":"
                + "[\"PRESENTATION\",\"APPLICATION\",\"PERSISTENCE\"]"), json);
        assertTrue(json.contains("\"includeSelfCyclesInCycleEntropy\":false"), json);
        assertTrue(json.contains("\"referencedMetrics\":[\"total_entropy\"]"), json);

        // Additive, exactly like Increment 24's confidenceByDimension:
        // every pre-existing key keeps its meaning and its place.
        assertTrue(json.contains("\"confidence\":"), json);
        assertTrue(json.contains("\"confidenceByDimension\":"), json);
        assertTrue(json.contains("\"totalEntropy\":"), json);
    }

    @Test
    void theReportCarriesTheSameGovernanceObjectItWasConfiguredWith() {
        PipelineConfig config = PipelineTest.config();

        PipelineReport report = Pipeline.run(config);

        assertEquals(config.governance(), report.governance());
        assertEquals(List.of(Role.PRESENTATION, Role.APPLICATION, Role.PERSISTENCE),
                List.copyOf(report.governance().layerPolicy().knownRoles()),
                "carried verbatim from the caller's declaration, not re-derived from the graph");
    }

    @Test
    void driftComparesValuesWithoutComparingTheGovernanceThatProducedThem() {
        // A recorded limitation, pinned rather than left silent: an
        // EntropySnapshot carries a subject id and dimension values and
        // nothing about the policy those values were measured under. So
        // comparing a stored baseline against a current scan is only
        // sound if the governance was identical, and nothing in the data
        // says whether it was - a policy change could read as code drift.
        //
        // Not fixed here, because fixing it forces the question "what
        // does Drift.compute do when the two policies differ - reject,
        // warn, annotate, compare anyway?", which is drift semantics and
        // belongs to OQ-14. Increment 25 delivers the enabling half: the
        // report now carries its policy, so a caller storing a baseline
        // has something to store.
        for (RecordComponent component : EntropySnapshot.class.getRecordComponents()) {
            assertFalse(component.getGenericType().getTypeName().contains("Governance"),
                    "EntropySnapshot gained governance identity - that is OQ-14's decision and needs its "
                            + "record: " + component);
        }
        assertEquals(List.of("subjectId", "dimensionValues"),
                java.util.Arrays.stream(EntropySnapshot.class.getRecordComponents())
                        .map(RecordComponent::getName).toList());
    }

    @Test
    void aDeclaredSubsystemReachesTheSerializedReportAndChangesWhichEdgesViolate() {
        // OQ-04's reachability proof: per-subsystem matrices have to work
        // through a real Pipeline.run and appear in the report, not merely
        // be unit-testable in the calculator.
        PipelineConfig base = PipelineTest.config();
        GovernancePolicy governance = base.governance();

        // The defect sample's own violation is a PRESENTATION node calling
        // a PERSISTENCE one. Declaring a subsystem over it whose matrix
        // permits exactly that must clear the violation without changing
        // how many edges were measured.
        LayerPolicy permissive = LayerPolicy.of(
                governance.layerPolicy().knownRoles(),
                Map.of(
                        Role.PRESENTATION, Set.of(Role.PRESENTATION, Role.APPLICATION, Role.PERSISTENCE),
                        Role.APPLICATION, Set.of(Role.APPLICATION, Role.PERSISTENCE),
                        Role.PERSISTENCE, Set.of(Role.PERSISTENCE)));

        PipelineReport strict = Pipeline.run(base);
        PipelineReport relaxed = Pipeline.run(new PipelineConfig(
                base.extraction(), base.detection(),
                new GovernancePolicy(
                        governance.layerPolicy(),
                        Subsystems.of(List.of(
                                Subsystem.withLayerPolicy("everything", "com", permissive))),
                        governance.includeSelfCyclesInCycleEntropy(),
                        governance.calibrationProfile(), governance.invariants(),
                        governance.invariantWeights(), governance.approvedExceptions())));

        assertEquals(strict.layerEntropy().relevantEdges().size(),
                relaxed.layerEntropy().relevantEdges().size(),
                "a subsystem declaration changes which matrix judges an edge, never which edges are in scope");
        assertTrue(strict.layerEntropy().violatingEdges().size() > relaxed.layerEntropy().violatingEdges().size(),
                "the permissive subsystem matrix must actually clear violations");
        assertEquals(strict.confidence(), relaxed.confidence(),
                "Amendment 7: a policy outcome never moves confidence");

        String json = JsonWriter.write(Main.toJson(relaxed));
        assertTrue(json.contains("\"name\":\"everything\""), json);
        assertTrue(json.contains("\"idPrefix\":\"com\""), json);
    }

    @Test
    void declaringNoSubsystemsLeavesTheReportSayingSoExplicitly() {
        String json = JsonWriter.write(Main.toJson(Pipeline.run(PipelineTest.config())));

        assertTrue(json.contains("\"subsystems\":[]"), json);
    }
}
