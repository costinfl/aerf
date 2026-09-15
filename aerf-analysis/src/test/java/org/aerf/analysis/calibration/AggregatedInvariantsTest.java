package org.aerf.analysis.calibration;

import org.aerf.analysis.governance.InvariantWeights;
import org.aerf.analysis.governance.WeightedInvariant;
import org.aerf.analysis.invariant.InvariantEvaluationResult;
import org.aerf.analysis.invariant.InvariantViolation;
import org.aerf.analysis.invariant.ViolationSubject;
import org.aerf.model.NodeId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Increment 29 (OQ-15): {@code E_inv = sum(lambda_k * I_k(S))}, AERF
 * v0.4 §6.1. See {@code docs/increment-29-invariant-aggregation.md}.
 */
class AggregatedInvariantsTest {

    @Test
    void theSumIsWeightTimesIndicatorPerSection61() {
        InvariantAggregate aggregate = AggregatedInvariants.compute(
                weights(weight("violated", 2.0), weight("holds", 5.0)),
                List.of(violated("violated"), holding("holds")),
                List.of());

        assertEquals(OptionalDouble.of(2.0), aggregate.value(),
                "only the violated invariant contributes: 2.0*1 + 5.0*0");
    }

    @Test
    void aHoldingInvariantContributesNothingButIsStillListed() {
        InvariantAggregate aggregate = AggregatedInvariants.compute(
                weights(weight("holds", 5.0)), List.of(holding("holds")), List.of());

        assertEquals(OptionalDouble.of(0.0), aggregate.value());
        assertEquals(1, aggregate.contributions().size(),
                "a term of zero is still a term - the reader sees it was weighted 5 and held");
        assertEquals(0, aggregate.contributions().get(0).indicatorValue());
    }

    @Test
    void theAggregateIsUnboundedUnlikeAnEntropyDimension() {
        // Section 6.1 states no normalization constraint on lambda_k, so
        // there is no denominator and no clamp. The direct analogue of
        // PersistenceEntropyResult.weightedValue()'s own headroom.
        InvariantAggregate aggregate = AggregatedInvariants.compute(
                weights(weight("a", 3.0), weight("b", 4.0)),
                List.of(violated("a"), violated("b")), List.of());

        assertEquals(OptionalDouble.of(7.0), aggregate.value(),
                "7.0, not something normalized into [0,1]");
    }

    @Test
    void everyWeightedInvariantsContributionStaysVisibleBesideTheTotal() {
        // OQ-15's "no violation disappears through aggregation", asserted
        // directly: the sum can always be read back to its own terms.
        InvariantAggregate aggregate = AggregatedInvariants.compute(
                weights(weight("a", 2.0), weight("b", 0.5)),
                List.of(violated("a"), violated("b")), List.of());

        assertEquals(List.of("a", "b"),
                aggregate.contributions().stream().map(InvariantContribution::invariantName).toList());
        assertEquals(aggregate.value().getAsDouble(),
                aggregate.contributions().stream().mapToDouble(InvariantContribution::contribution).sum(),
                "the total is exactly the sum of the terms reported beside it");
    }

    @Test
    void aWeightedInvariantThatWasSkippedMakesTheAggregateUndefined() {
        // The AggregatedEntropy mirror: a skipped invariant produced no
        // I_k(S) at all, and coercing that to 0 would claim the invariant
        // holds where it was never checked.
        InvariantAggregate aggregate = AggregatedInvariants.compute(
                weights(weight("evaluated", 1.0), weight("skipped", 4.0)),
                List.of(violated("evaluated")), List.of("skipped"));

        assertTrue(aggregate.value().isEmpty());
        assertEquals(List.of("skipped"), aggregate.unevaluatedWeightedInvariants(),
                "an undefined total carries its own reason");
    }

    @Test
    void anUndefinedTotalStillReportsTheTermsThatWereEvaluated() {
        InvariantAggregate aggregate = AggregatedInvariants.compute(
                weights(weight("evaluated", 1.0), weight("skipped", 4.0)),
                List.of(violated("evaluated")), List.of("skipped"));

        assertEquals(1, aggregate.contributions().size(),
                "the evaluated invariant has a real indicator value; hiding it would lose evidence");
    }

    @Test
    void aZeroWeightedInvariantBeingSkippedDoesNotBlockAggregation() {
        // Exactly AggregatedEntropy's exemption: it does not actually
        // contribute to the sum, so its absence cannot change it.
        InvariantAggregate aggregate = AggregatedInvariants.compute(
                weights(weight("evaluated", 3.0), weight("skipped", 0.0)),
                List.of(violated("evaluated")), List.of("skipped"));

        assertEquals(OptionalDouble.of(3.0), aggregate.value());
        assertTrue(aggregate.unevaluatedWeightedInvariants().isEmpty());
    }

    @Test
    void weightsMustCoverEveryConfiguredInvariantOrConstructionThrows() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> AggregatedInvariants.compute(
                        weights(weight("a", 1.0)),
                        List.of(violated("a"), violated("unweighted")), List.of()));

        assertTrue(thrown.getMessage().contains("unweighted"), thrown.getMessage());
    }

    @Test
    void aSkippedInvariantStillNeedsAWeightSoItCannotHideFromCoverage() {
        assertThrows(IllegalArgumentException.class,
                () -> AggregatedInvariants.compute(
                        weights(weight("a", 1.0)), List.of(violated("a")), List.of("skipped-and-unweighted")));
    }

    @Test
    void weightingAnInvariantThatWasNeverConfiguredIsRejected() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> AggregatedInvariants.compute(
                        weights(weight("a", 1.0), weight("ghost", 1.0)), List.of(violated("a")), List.of()));

        assertTrue(thrown.getMessage().contains("ghost"), thrown.getMessage());
    }

    @Test
    void noDeclaredWeightsLeavesTheAggregateUndefinedRatherThanZero() {
        // Declaring no importances is not a statement that an
        // organization's invariants are unimportant.
        InvariantAggregate aggregate = AggregatedInvariants.compute(
                InvariantWeights.none(), List.of(violated("a")), List.of());

        assertTrue(aggregate.value().isEmpty());
        assertTrue(aggregate.contributions().isEmpty());
    }

    @Test
    void contributionsFollowDeclarationOrderNotEvaluationOrder() {
        InvariantAggregate aggregate = AggregatedInvariants.compute(
                weights(weight("second", 1.0), weight("first", 1.0)),
                List.of(violated("first"), violated("second")), List.of());

        assertEquals(List.of("second", "first"),
                aggregate.contributions().stream().map(InvariantContribution::invariantName).toList());
    }

    @Test
    void resultIsDeterministicAcrossRepeatedRuns() {
        InvariantWeights weights = weights(weight("a", 2.0), weight("b", 3.0));
        List<InvariantEvaluationResult> results = List.of(violated("a"), holding("b"));

        assertEquals(AggregatedInvariants.compute(weights, results, List.of()),
                AggregatedInvariants.compute(weights, results, List.of()));
    }

    @Test
    void anIndicatorValueOtherThanZeroOrOneIsUnrepresentable() {
        assertThrows(IllegalArgumentException.class,
                () -> new InvariantContribution("a", 1.0, 2, 2.0));
    }

    private static InvariantWeights weights(WeightedInvariant... declared) {
        return InvariantWeights.of(List.of(declared));
    }

    private static WeightedInvariant weight(String name, double weight) {
        return new WeightedInvariant(name, weight);
    }

    private static InvariantEvaluationResult violated(String name) {
        return new InvariantEvaluationResult(name, "critical", List.of(
                new InvariantViolation(new ViolationSubject.OfNode(NodeId.of("x")), "failed")));
    }

    private static InvariantEvaluationResult holding(String name) {
        return new InvariantEvaluationResult(name, "critical", List.of());
    }
}
