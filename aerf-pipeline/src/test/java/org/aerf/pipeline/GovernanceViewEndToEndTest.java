package org.aerf.pipeline;

import org.aerf.analysis.calibration.EntropySnapshot;
import org.aerf.analysis.governance.ApprovedException;
import org.aerf.analysis.governance.ApprovedExceptions;
import org.aerf.analysis.governance.DriftSensitivity;
import org.aerf.analysis.governance.ExceptionTarget;
import org.aerf.analysis.governance.GovernancePolicy;
import org.aerf.analysis.governance.InvariantWeights;
import org.aerf.analysis.governance.Subsystem;
import org.aerf.analysis.governance.Subsystems;
import org.aerf.analysis.governance.WeightedDriftDimension;
import org.aerf.analysis.governance.WeightedInvariant;
import org.aerf.analysis.metrics.layer.LayerPolicy;
import org.aerf.analysis.view.DimensionObservation;
import org.aerf.analysis.view.GovernanceView;
import org.aerf.analysis.view.TraceableFinding;
import org.aerf.analysis.view.ViolatedConstraint;
import org.aerf.model.Edge;
import org.aerf.model.NodeRef;
import org.aerf.model.Role;
import org.aerf.report.GovernanceViewJson;
import org.aerf.report.json.JsonWriter;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Increment 31 (OQ-16)'s reachability proof: the view composed from a
 * genuine {@link Pipeline#run}, answering all five commissioned questions
 * and serializing.
 *
 * <p>The baseline side is caller-supplied for the reason
 * {@link DriftEndToEndTest} and {@link RiskEndToEndTest} both give: this
 * pipeline has no prior measurement and deliberately does not reach into
 * storage for one. That is also why the view reaches no report key.
 */
class GovernanceViewEndToEndTest {

    private static final String SUBJECT_ID = "defect-sample";

    @Test
    void aRealRunAnswersTheThreeQuestionsThatNeedNoBaselineAndNamesTheTwoThatDo() {
        PipelineReport report = Pipeline.run(PipelineTest.config());
        GovernanceView view = report.toGovernanceView(SUBJECT_ID);

        // 1. what architectural condition was observed
        assertEquals(List.of("layer", "cycle", "persistence", "security"),
                view.observed().stream().map(DimensionObservation::dimension).toList());
        assertEquals(report.totalEntropy(), view.totalEntropy());
        assertEquals(report.maturity(), view.maturity());
        assertEquals(report.confidence(), view.confidence());

        // Each observation reconstructs its own metric's ratio.
        DimensionObservation layer = view.observationOf("layer").orElseThrow();
        assertEquals(report.layerEntropy().relevantEdges().size(), layer.relevantCount());
        assertEquals(report.layerEntropy().violatingEdges().size(), layer.findingCount());
        assertEquals(report.layerEntropy().value(), layer.value());

        // 3. what governance constraints were violated - every configured
        // invariant, held ones included.
        assertEquals(report.invariantResults().size(), view.violatedConstraints().size());
        assertSameInstance(report.invariantAggregate(), view.invariantAggregate());

        // 5. what evidence supports each conclusion
        assertFalse(view.findings().isEmpty(), "this fixture has real findings");

        // 2 and 4 are named as unanswered rather than shown empty.
        assertEquals(2, view.unanswered().size(), view.unanswered().toString());
        assertTrue(view.unanswered().get(0).contains("no baseline was supplied"), view.unanswered().toString());
    }

    @Test
    void aBaselineCompletesTheRemainingTwoQuestionsThroughTheSamePureFunctions() {
        PipelineReport report = Pipeline.run(sensitiveConfig());
        EntropySnapshot current = report.toEntropySnapshot(SUBJECT_ID);
        Map<String, OptionalDouble> priorValues = new LinkedHashMap<>(current.dimensionValues());
        priorValues.put("layer", OptionalDouble.of(0.6));
        priorValues.put("persistence", OptionalDouble.of(0.0));
        // Same subjectId on both sides: Drift.compute rejects two
        // snapshots of different subjects, because section 5.3's drift is
        // one system over time. That is also why BaselineComparison names
        // no second subject.
        EntropySnapshot baseline =
                new EntropySnapshot(SUBJECT_ID, priorValues, current.governanceFingerprint());

        GovernanceView view = report.toGovernanceView(SUBJECT_ID, baseline);

        assertEquals(List.of(), view.unanswered(), "all five questions are answered");

        // Exactly the numbers RiskEndToEndTest establishes for this
        // baseline: layer 0.6 -> 1.0 and persistence 0.0 -> 0.5, both
        // weighted 1.0, beta 2.0. The view composes; it does not compute
        // its own.
        assertEquals(report.totalEntropy(), view.comparison().orElseThrow().risk().entropyTerm());
        assertEquals(OptionalDouble.of(2.0 * (0.4 + 0.5)), view.comparison().orElseThrow().risk().driftTerm());
        assertEquals(OptionalDouble.of(0.1), OptionalDouble.of(round(
                view.comparison().orElseThrow().drift().get("layer").delta() - 0.3)));
    }

    @Test
    void aBaselineFromADifferentPolicyLeavesRiskUnansweredWithRiskSOwnReason() {
        // Finding B's refusal surfacing through the view, unparaphrased.
        PipelineReport strict = Pipeline.run(sensitiveConfig());
        PipelineReport relaxed = Pipeline.run(relaxedSensitiveConfig());

        GovernanceView view = strict.toGovernanceView(SUBJECT_ID, relaxed.toEntropySnapshot(SUBJECT_ID));

        assertTrue(view.comparison().isPresent(), "drift was still computed - Drift subtracts, Risk interprets");
        assertTrue(view.comparison().orElseThrow().risk().value().isEmpty());
        assertTrue(view.unanswered().stream().anyMatch(gap ->
                        gap.startsWith("what risk interpretation follows:") && gap.contains("different policies")),
                view.unanswered().toString());
    }

    @Test
    void aLayerViolationNamesTheSubsystemWhoseMatrixJudgedIt() {
        // Finding E, resolved end to end. Every node in this fixture is
        // under com.example, so declaring a subsystem with its own matrix
        // over that prefix means the subsystem - not the default - judged
        // every layer finding, and the view says so.
        PipelineReport report = Pipeline.run(withSubsystems(Subsystems.of(List.of(
                Subsystem.withLayerPolicy("orders", "com.example", PipelineTest.config()
                        .governance().layerPolicy())))));
        GovernanceView view = report.toGovernanceView(SUBJECT_ID);

        List<TraceableFinding> layerFindings = view.findings().stream()
                .filter(finding -> finding.dimension().equals("layer")).toList();
        assertFalse(layerFindings.isEmpty(), "the fixture violates layering");
        for (TraceableFinding finding : layerFindings) {
            assertEquals(Optional.of("orders"), finding.governedBy(),
                    "the subsystem's own matrix judged this edge");
        }
    }

    @Test
    void aSubsystemWithNoMatrixIsNotNamedAsTheJudgeBecauseTheDefaultJudged() {
        PipelineReport report = Pipeline.run(withSubsystems(Subsystems.of(List.of(
                Subsystem.of("orders", "com.example")))));
        GovernanceView view = report.toGovernanceView(SUBJECT_ID);

        List<TraceableFinding> layerFindings = view.findings().stream()
                .filter(finding -> finding.dimension().equals("layer")).toList();
        assertFalse(layerFindings.isEmpty());
        for (TraceableFinding finding : layerFindings) {
            assertTrue(finding.governedBy().isEmpty(),
                    "claimed by 'orders', judged by the default matrix - the view reports the judge");
        }
    }

    @Test
    void anExcusedFindingIsMarkedInTheViewAndStillCounted() {
        PipelineReport plain = Pipeline.run(PipelineTest.config());
        Edge violation = plain.layerEntropy().violatingEdges().get(0);
        ExceptionTarget target = targetOf(violation);

        PipelineReport excused = Pipeline.run(withException(
                new ApprovedException(target, "accepted for the legacy admin screen", "alice")));
        GovernanceView view = excused.toGovernanceView(SUBJECT_ID);

        assertEquals(plain.toGovernanceView(SUBJECT_ID).observed(), view.observed(),
                "excusal is a governance verdict and moves no measured count");
        assertTrue(view.findings().stream().anyMatch(finding ->
                        finding.subject().equals(Optional.of(target))
                                && finding.excusedBy().map(ApprovedException::approvedBy)
                                        .equals(Optional.of("alice"))),
                view.findings().toString());
        assertTrue(view.unexcusedFindings().size() < view.findings().size());
    }

    @Test
    void eachInvariantAppearsWithItsIndicatorAndWeightWhetherOrNotItHeld() {
        PipelineReport report = Pipeline.run(withWeights(InvariantWeights.of(List.of(
                new WeightedInvariant("no_presentation_to_persistence", 1.0),
                new WeightedInvariant("entropy_budget", 4.0)))));
        GovernanceView view = report.toGovernanceView(SUBJECT_ID);

        assertEquals(2, view.violatedConstraints().size());
        for (ViolatedConstraint constraint : view.violatedConstraints()) {
            assertTrue(constraint.weight().isPresent(), constraint.invariantName());
            assertEquals(report.invariantResults().stream()
                            .filter(r -> r.invariantName().equals(constraint.invariantName()))
                            .findFirst().orElseThrow().indicatorValue(),
                    constraint.indicatorValue());
        }
    }

    @Test
    void theViewReachesJsonWithTheFiveQuestionsAsItsOwnStructure() {
        PipelineReport report = Pipeline.run(sensitiveConfig());
        EntropySnapshot current = report.toEntropySnapshot(SUBJECT_ID);
        Map<String, OptionalDouble> priorValues = new LinkedHashMap<>(current.dimensionValues());
        priorValues.put("layer", OptionalDouble.of(0.6));
        EntropySnapshot baseline = new EntropySnapshot(SUBJECT_ID, priorValues, current.governanceFingerprint());

        String json = JsonWriter.write(GovernanceViewJson.view(report.toGovernanceView(SUBJECT_ID, baseline)));

        assertTrue(json.contains("\"observed\":"), json);
        assertTrue(json.contains("\"changed\":"), json);
        assertTrue(json.contains("\"violated\":"), json);
        assertTrue(json.contains("\"risk\":"), json);
        assertTrue(json.contains("\"findings\":"), json);
        assertTrue(json.contains("\"unanswered\":[]"), json);
    }

    @Test
    void withoutABaselineTheTwoBaselineSectionsSerializeAsNullRatherThanEmptyObjects() {
        String json = JsonWriter.write(
                GovernanceViewJson.view(Pipeline.run(PipelineTest.config()).toGovernanceView(SUBJECT_ID)));

        assertTrue(json.contains("\"changed\":null"), json);
        assertTrue(json.contains("\"risk\":null"), json);
        assertTrue(json.contains("no baseline was supplied"), json);
    }

    @Test
    void theViewIsNotAPipelineReportComponentAndReachesNoReportKey() {
        // Two of its five answers need a baseline the pipeline does not
        // have, so - like R - it is composed by a caller, not emitted by a
        // scan. A default scan must be unchanged by this increment.
        assertTrue(Arrays.stream(PipelineReport.class.getRecordComponents())
                        .noneMatch(c -> c.getType().getSimpleName().contains("GovernanceView")),
                "the view is derived on demand, not carried");
        String json = JsonWriter.write(Main.toJson(Pipeline.run(PipelineTest.config())));
        assertFalse(json.contains("\"governanceView\""), json);
        assertFalse(json.contains("\"unanswered\""), json);
    }

    @Test
    void composingAViewChangesNothingAboutTheReportItWasComposedFrom() {
        PipelineReport report = Pipeline.run(PipelineTest.config());
        String before = JsonWriter.write(Main.toJson(report));

        report.toGovernanceView(SUBJECT_ID);
        report.toGovernanceView(SUBJECT_ID, report.toEntropySnapshot(SUBJECT_ID));

        assertEquals(before, JsonWriter.write(Main.toJson(report)),
                "the view is strictly downstream - it reads and never writes back");
    }

    private static double round(double value) {
        return Math.round(value * 1e9) / 1e9;
    }

    private static ExceptionTarget targetOf(Edge edge) {
        return new ExceptionTarget.OfEdge(
                ((NodeRef.Resolved) edge.source()).id().value(),
                ((NodeRef.Resolved) edge.target()).id().value(),
                edge.relation());
    }

    private static PipelineConfig withException(ApprovedException exception) {
        return replaceGovernance(g -> new GovernancePolicy(
                g.layerPolicy(), g.subsystems(), g.includeSelfCyclesInCycleEntropy(),
                g.calibrationProfile(), g.invariants(), g.invariantWeights(), g.driftSensitivity(),
                ApprovedExceptions.of(List.of(exception))));
    }

    private static PipelineConfig withWeights(InvariantWeights weights) {
        return replaceGovernance(g -> new GovernancePolicy(
                g.layerPolicy(), g.subsystems(), g.includeSelfCyclesInCycleEntropy(),
                g.calibrationProfile(), g.invariants(), weights, g.driftSensitivity(),
                g.approvedExceptions()));
    }

    private static PipelineConfig withSubsystems(Subsystems subsystems) {
        return replaceGovernance(g -> new GovernancePolicy(
                g.layerPolicy(), subsystems, g.includeSelfCyclesInCycleEntropy(),
                g.calibrationProfile(), g.invariants(), g.invariantWeights(), g.driftSensitivity(),
                g.approvedExceptions()));
    }

    private static PipelineConfig sensitiveConfig() {
        return withSensitivity(PipelineTest.config().governance().layerPolicy());
    }

    private static PipelineConfig relaxedSensitiveConfig() {
        return withSensitivity(LayerPolicy.of(
                Set.of(Role.PRESENTATION, Role.APPLICATION, Role.PERSISTENCE),
                Map.of(Role.PRESENTATION, Set.of(Role.PRESENTATION, Role.APPLICATION, Role.PERSISTENCE),
                        Role.APPLICATION, Set.of(Role.APPLICATION, Role.PERSISTENCE),
                        Role.PERSISTENCE, Set.of(Role.PERSISTENCE))));
    }

    private static PipelineConfig withSensitivity(LayerPolicy layerPolicy) {
        return replaceGovernance(g -> new GovernancePolicy(
                layerPolicy, Subsystems.none(), g.includeSelfCyclesInCycleEntropy(),
                g.calibrationProfile(), g.invariants(), InvariantWeights.none(),
                DriftSensitivity.of(2.0, List.of(
                        new WeightedDriftDimension("layer", 1.0),
                        new WeightedDriftDimension("persistence", 1.0))),
                ApprovedExceptions.none()));
    }

    private static PipelineConfig replaceGovernance(
            java.util.function.UnaryOperator<GovernancePolicy> replacement) {
        PipelineConfig base = PipelineTest.config();
        return new PipelineConfig(base.extraction(), base.detection(),
                replacement.apply(base.governance()));
    }

    private static void assertSameInstance(Object expected, Object actual) {
        assertTrue(expected == actual, "expected the very same instance, not an equal copy");
    }
}
