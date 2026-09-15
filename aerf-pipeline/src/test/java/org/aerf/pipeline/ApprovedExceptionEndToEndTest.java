package org.aerf.pipeline;

import org.aerf.analysis.governance.ApprovedException;
import org.aerf.analysis.governance.ApprovedExceptions;
import org.aerf.analysis.governance.ExceptionTarget;
import org.aerf.analysis.governance.ExcusedFinding;
import org.aerf.analysis.governance.GovernancePolicy;
import org.aerf.model.Edge;
import org.aerf.model.NodeRef;
import org.aerf.report.json.JsonWriter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Increment 28 (OQ-09)'s reachability proof: an approved exception has to
 * work through a real {@link Pipeline#run} and reach the serialized
 * report — the standard the V04-CAL-02 correction established.
 *
 * <p>More importantly, this is where the increment's central claim is
 * demonstrated rather than argued: excusing a real finding on real parsed
 * source leaves every measured value byte-identical.
 */
class ApprovedExceptionEndToEndTest {

    @Test
    void excusingARealFindingChangesNoMeasuredValueAnywhereInTheReport() {
        PipelineReport plain = Pipeline.run(PipelineTest.config());
        Edge violation = plain.layerEntropy().violatingEdges().get(0);

        PipelineReport excused = Pipeline.run(withException(new ApprovedException(
                targetOf(violation), "accepted for the legacy admin screen", "alice")));

        assertEquals(plain.layerEntropy().value(), excused.layerEntropy().value());
        assertEquals(plain.cycleEntropy().value(), excused.cycleEntropy().value());
        assertEquals(plain.persistenceEntropy().value(), excused.persistenceEntropy().value());
        assertEquals(plain.persistenceEntropy().weightedValue(), excused.persistenceEntropy().weightedValue());
        assertEquals(plain.securityEntropy().value(), excused.securityEntropy().value());
        assertEquals(plain.totalEntropy(), excused.totalEntropy());
        assertEquals(plain.maturity(), excused.maturity());
        assertEquals(plain.confidence(), excused.confidence());
        assertEquals(plain.confidenceByDimension(), excused.confidenceByDimension());
    }

    @Test
    void theExcusedFindingIsStillReportedInFullWithItsEvidence() {
        PipelineReport plain = Pipeline.run(PipelineTest.config());
        Edge violation = plain.layerEntropy().violatingEdges().get(0);

        PipelineReport excused = Pipeline.run(withException(new ApprovedException(
                targetOf(violation), "accepted", "alice")));

        assertEquals(plain.layerEntropy().violatingEdges(), excused.layerEntropy().violatingEdges(),
                "never silently delete or mutate source evidence - the finding and its provenance are identical");
        assertTrue(excused.exceptionLedger().excused().stream()
                        .anyMatch(e -> e.dimension().equals("layer")),
                excused.exceptionLedger().toString());
    }

    @Test
    void theLedgerAndTheDeclarationBothReachTheSerializedReport() {
        PipelineReport plain = Pipeline.run(PipelineTest.config());
        PipelineReport excused = Pipeline.run(withException(new ApprovedException(
                targetOf(plain.layerEntropy().violatingEdges().get(0)),
                "accepted until the Q3 rewrite", "alice")));

        String json = JsonWriter.write(Main.toJson(excused));

        assertTrue(json.contains("\"exceptionLedger\":"), json);
        assertTrue(json.contains("\"approvedBy\":\"alice\""), json);
        assertTrue(json.contains("\"reason\":\"accepted until the Q3 rewrite\""), json);
        assertTrue(json.contains("\"approvedExceptions\":["), "the declaration appears under governance too");
    }

    @Test
    void aStaleExceptionSurvivesTheWholePipelineAsUnmatched() {
        PipelineReport report = Pipeline.run(withException(new ApprovedException(
                new ExceptionTarget.OfNode("com.example.DeletedLastYear"), "was a known issue", "bob")));

        assertEquals(1, report.exceptionLedger().unmatched().size(),
                "an exception matching nothing is reported, not dropped");
        assertTrue(report.exceptionLedger().excused().isEmpty());
        assertTrue(JsonWriter.write(Main.toJson(report)).contains("com.example.DeletedLastYear"));
    }

    @Test
    void declaringNoExceptionsLeavesAnEmptyLedgerInTheReport() {
        PipelineReport report = Pipeline.run(PipelineTest.config());

        assertTrue(report.exceptionLedger().isEmpty());
        String json = JsonWriter.write(Main.toJson(report));
        assertTrue(json.contains("\"exceptionLedger\":{\"excused\":[],\"unmatched\":[]}"), json);
        assertFalse(json.contains("\"approvedBy\""), json);
    }

    @Test
    void everyExcusalNamesTheDimensionItCameFrom() {
        PipelineReport plain = Pipeline.run(PipelineTest.config());
        PipelineReport excused = Pipeline.run(withException(new ApprovedException(
                targetOf(plain.layerEntropy().violatingEdges().get(0)), "accepted", "alice")));

        for (ExcusedFinding finding : excused.exceptionLedger().excused()) {
            assertFalse(finding.dimension().isBlank());
            assertEquals("alice", finding.approvedBy());
        }
    }

    private static ExceptionTarget targetOf(Edge edge) {
        return new ExceptionTarget.OfEdge(
                ((NodeRef.Resolved) edge.source()).id().value(),
                ((NodeRef.Resolved) edge.target()).id().value(),
                edge.relation());
    }

    private static PipelineConfig withException(ApprovedException exception) {
        PipelineConfig base = PipelineTest.config();
        GovernancePolicy g = base.governance();
        return new PipelineConfig(base.extraction(), base.detection(), new GovernancePolicy(
                g.layerPolicy(), g.subsystems(), g.includeSelfCyclesInCycleEntropy(),
                g.calibrationProfile(), g.invariants(), g.invariantWeights(), g.driftSensitivity(),
                ApprovedExceptions.of(List.of(exception))));
    }
}
