package org.aerf.pipeline;

import org.aerf.analysis.calibration.CalibrationProfile;
import org.aerf.analysis.calibration.InvariantAggregate;
import org.aerf.analysis.calibration.InvariantContribution;
import org.aerf.analysis.calibration.LinearCalibration;
import org.aerf.analysis.calibration.WeightedDimension;
import org.aerf.analysis.governance.GovernancePolicy;
import org.aerf.analysis.governance.InvariantWeights;
import org.aerf.analysis.governance.WeightedInvariant;
import org.aerf.report.json.JsonWriter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Increment 29 (OQ-15)'s reachability proof: §6.1's {@code E_inv} has to
 * be computed and serialized through a real {@link Pipeline#run} — the
 * standard the V04-CAL-02 correction established.
 *
 * <p>The second test is the one that matters most. OQ-15's acceptance
 * requires that undefined results have explicit semantics and that no
 * violation disappears through aggregation; the pipeline's existing
 * skipped-invariant path is exactly where both could go wrong.
 */
class InvariantAggregateEndToEndTest {

    private static final String LAYER_RULE = "no_presentation_to_persistence";
    private static final String BUDGET_RULE = "entropy_budget";

    @Test
    void aRealRunComputesEinvFromItsOwnInvariantResults() {
        // defect-sample has a deliberate Presentation->Persistence edge, so
        // the layer invariant is violated; and its total entropy of 0.5 is
        // over the 0.35 budget PipelineTest.config() declares, so the
        // budget invariant is violated too. E_inv = 2.0*1 + 5.0*1.
        PipelineReport report = Pipeline.run(withWeights(
                new WeightedInvariant(LAYER_RULE, 2.0),
                new WeightedInvariant(BUDGET_RULE, 5.0)));

        InvariantAggregate aggregate = report.invariantAggregate();

        assertEquals(OptionalDouble.of(0.5), report.totalEntropy(), "the reason the budget rule fails");
        assertEquals(OptionalDouble.of(7.0), aggregate.value());
        assertEquals(List.of(LAYER_RULE, BUDGET_RULE),
                aggregate.contributions().stream().map(InvariantContribution::invariantName).toList());
        assertEquals(1, contribution(aggregate, LAYER_RULE).indicatorValue());
        assertEquals(1, contribution(aggregate, BUDGET_RULE).indicatorValue());
        assertEquals(aggregate.value().getAsDouble(),
                aggregate.contributions().stream().mapToDouble(InvariantContribution::contribution).sum(),
                "the total is exactly the sum of the terms reported beside it");
    }

    @Test
    void aWeightedButSkippedInvariantLeavesEinvUndefinedWhileStillBeingNamed() {
        // Reuses PipelineTest's own skipped-invariant setup: weighting
        // security nonzero leaves total_entropy undefined, so the
        // GRAPH-scope entropy_budget invariant is never evaluated.
        //
        // Both halves of OQ-15's acceptance are asserted here. E_inv is
        // undefined rather than silently summing as if the invariant held,
        // and the skipped invariant is still named in two places - so
        // nothing disappears.
        PipelineReport report = Pipeline.run(withWeightsAndSecurityWeighted(
                new WeightedInvariant(LAYER_RULE, 2.0),
                new WeightedInvariant(BUDGET_RULE, 5.0)));

        assertTrue(report.totalEntropy().isEmpty(), "the precondition this test depends on");
        assertEquals(List.of(BUDGET_RULE), report.skippedInvariants());

        InvariantAggregate aggregate = report.invariantAggregate();
        assertTrue(aggregate.value().isEmpty(), "a weighted invariant was never checked");
        assertEquals(List.of(BUDGET_RULE), aggregate.unevaluatedWeightedInvariants());
        assertEquals(1, aggregate.contributions().size(),
                "the layer invariant was evaluated and its term stays visible");
    }

    @Test
    void aZeroWeightedSkippedInvariantDoesNotBlockTheAggregate() {
        PipelineReport report = Pipeline.run(withWeightsAndSecurityWeighted(
                new WeightedInvariant(LAYER_RULE, 3.0),
                new WeightedInvariant(BUDGET_RULE, 0.0)));

        assertEquals(OptionalDouble.of(3.0), report.invariantAggregate().value());
        assertTrue(report.invariantAggregate().unevaluatedWeightedInvariants().isEmpty());
    }

    @Test
    void declaringNoWeightsLeavesEinvUndefinedInTheReport() {
        PipelineReport report = Pipeline.run(PipelineTest.config());

        assertTrue(report.invariantAggregate().value().isEmpty());
        assertTrue(report.invariantAggregate().contributions().isEmpty());
    }

    @Test
    void einvDoesNotEnterTotalEntropyOrMaturity() {
        // Section 6.1 imposes no normalization on lambda_k, so E_inv is
        // unbounded and is not an entropy dimension. Declaring weights
        // must therefore move nothing in the section 5 aggregate.
        PipelineReport unweighted = Pipeline.run(PipelineTest.config());
        PipelineReport weighted = Pipeline.run(withWeights(
                new WeightedInvariant(LAYER_RULE, 2.0), new WeightedInvariant(BUDGET_RULE, 5.0)));

        assertEquals(unweighted.totalEntropy(), weighted.totalEntropy());
        assertEquals(unweighted.maturity(), weighted.maturity());
        assertEquals(unweighted.maturityLevel(), weighted.maturityLevel());
        assertEquals(unweighted.entropyByDimension(), weighted.entropyByDimension());
        assertEquals(List.of("layer", "cycle", "persistence", "security"),
                List.copyOf(weighted.entropyByDimension().keySet()),
                "still exactly the four section 4 dimensions - E_inv is not a fifth");
    }

    @Test
    void theAggregateAndTheDeclaredWeightsBothReachTheSerializedReport() {
        PipelineReport report = Pipeline.run(withWeights(
                new WeightedInvariant(LAYER_RULE, 2.0), new WeightedInvariant(BUDGET_RULE, 5.0)));

        String json = JsonWriter.write(Main.toJson(report));

        assertTrue(json.contains("\"invariantAggregate\":{\"value\":7.0"), json);
        assertTrue(json.contains("\"invariantName\":\"" + LAYER_RULE + "\",\"weight\":2.0"), json);
        assertTrue(json.contains("\"invariantWeights\":["), "the declaration appears under governance too");
    }

    private static InvariantContribution contribution(InvariantAggregate aggregate, String name) {
        return aggregate.contributions().stream()
                .filter(c -> c.invariantName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no contribution for " + name));
    }

    private static PipelineConfig withWeights(WeightedInvariant... weights) {
        PipelineConfig base = PipelineTest.config();
        return withGovernance(base, base.governance().calibrationProfile(), weights);
    }

    private static PipelineConfig withWeightsAndSecurityWeighted(WeightedInvariant... weights) {
        PipelineConfig base = PipelineTest.config();
        return withGovernance(base, CalibrationProfile.of(List.of(
                new WeightedDimension("layer", 0.25, new LinearCalibration()),
                new WeightedDimension("cycle", 0.25, new LinearCalibration()),
                new WeightedDimension("persistence", 0.25, new LinearCalibration()),
                new WeightedDimension("security", 0.25, new LinearCalibration()))), weights);
    }

    private static PipelineConfig withGovernance(PipelineConfig base, CalibrationProfile calibration,
                                                 WeightedInvariant... weights) {
        GovernancePolicy g = base.governance();
        return new PipelineConfig(base.extraction(), base.detection(), new GovernancePolicy(
                g.layerPolicy(), g.subsystems(), g.includeSelfCyclesInCycleEntropy(),
                calibration, g.invariants(), InvariantWeights.of(List.of(weights)), g.driftSensitivity(),
                g.approvedExceptions()));
    }
}
