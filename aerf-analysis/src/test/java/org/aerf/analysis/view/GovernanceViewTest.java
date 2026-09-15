package org.aerf.analysis.view;

import org.aerf.analysis.calibration.DimensionDrift;
import org.aerf.analysis.calibration.DriftPenalty;
import org.aerf.analysis.calibration.InvariantAggregate;
import org.aerf.analysis.calibration.InvariantContribution;
import org.aerf.analysis.calibration.MaturityLevel;
import org.aerf.analysis.calibration.RiskAssessment;
import org.aerf.analysis.governance.ApprovedException;
import org.aerf.analysis.governance.ExceptionTarget;
import org.aerf.model.RelationType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Increment 31 (OQ-16): the view's own semantics — what it can and cannot
 * answer, and what it refuses to do to the things it reports.
 */
class GovernanceViewTest {

    private static final ApprovedException EXCEPTION = new ApprovedException(
            new ExceptionTarget.OfNode("com.example.Legacy"),
            "accepted during the 2026 migration", "alice");

    @Test
    void withoutABaselineTheViewSaysSoRatherThanShowingAnEmptyComparison() {
        // The distinction that matters to a governance reader: an empty
        // drift section and a system that genuinely did not move would
        // serialize identically, so silence would be a claim.
        GovernanceView view = view(Optional.empty(), List.of());

        assertTrue(view.comparison().isEmpty());
        List<String> unanswered = view.unanswered();
        assertEquals(2, unanswered.size(), unanswered.toString());
        assertTrue(unanswered.get(0).startsWith("what changed relative to baseline:"), unanswered.get(0));
        assertTrue(unanswered.get(0).contains("not a statement that nothing changed"), unanswered.get(0));
        assertTrue(unanswered.get(1).startsWith("what risk interpretation follows:"), unanswered.get(1));
    }

    @Test
    void withABaselineAndADefinedRiskNothingIsUnanswered() {
        GovernanceView view = view(Optional.of(new BaselineComparison(
                Map.of("layer", new DimensionDrift("layer", 0.2, 0.3, 0.1)),
                new RiskAssessment(OptionalDouble.of(0.8), OptionalDouble.of(0.5), OptionalDouble.of(0.3),
                        List.of(new DriftPenalty("layer", 1.0, 0.1, 0.1)), List.of()))), List.of());

        assertEquals(List.of(), view.unanswered());
    }

    @Test
    void anUndefinedRiskSurfacesItsOwnReasonsAsUnansweredQuestions() {
        // Risk's refusal is reported as Risk stated it, not paraphrased -
        // a view that reworded it could soften a refusal into a caveat.
        GovernanceView view = view(Optional.of(new BaselineComparison(
                Map.of(),
                new RiskAssessment(OptionalDouble.empty(), OptionalDouble.of(0.5), OptionalDouble.empty(),
                        List.of(), List.of("the two measurements were governed by different policies")))),
                List.of());

        assertEquals(List.of("what risk interpretation follows: the two measurements were governed by "
                + "different policies"), view.unanswered());
    }

    @Test
    void aFindingThatNamesNothingAddressableIsListedAndSaidToBeUnaddressable() {
        // A cycle finding is the real case: section 4.2's unit is a set of
        // nodes, so there is no identifier for the finding itself.
        GovernanceView view = view(Optional.empty(), List.of(
                new TraceableFinding("cycle", Optional.empty(), Optional.empty(), Optional.empty())));

        assertEquals(1, view.findings().size(), "it is still a finding and is still listed");
        assertTrue(view.unanswered().stream().anyMatch(gap ->
                        gap.startsWith("what evidence supports each conclusion:") && gap.contains("1 finding(s)")),
                view.unanswered().toString());
    }

    @Test
    void anExcusedFindingIsStillListedAndNamesItsApprover() {
        // Increment 28 decided an approved exception ACCEPTS a finding
        // rather than denying it. The view carries that through: excusal
        // marks, never removes.
        TraceableFinding excused = new TraceableFinding(
                "security", Optional.of(EXCEPTION.target()), Optional.empty(), Optional.of(EXCEPTION));
        TraceableFinding open = new TraceableFinding(
                "security", Optional.of(new ExceptionTarget.OfNode("com.example.Other")),
                Optional.empty(), Optional.empty());
        GovernanceView view = view(Optional.empty(), List.of(excused, open));

        assertEquals(2, view.findings().size());
        assertTrue(excused.isExcused());
        assertEquals("alice", excused.excusedBy().orElseThrow().approvedBy());
        assertEquals(List.of(open), view.unexcusedFindings());
    }

    @Test
    void excusingAFindingMovesNoMeasuredCount() {
        GovernanceView withExcusal = view(Optional.empty(), List.of(new TraceableFinding(
                "security", Optional.of(EXCEPTION.target()), Optional.empty(), Optional.of(EXCEPTION))));
        GovernanceView without = view(Optional.empty(), List.of(new TraceableFinding(
                "security", Optional.of(EXCEPTION.target()), Optional.empty(), Optional.empty())));

        assertEquals(without.observed(), withExcusal.observed(),
                "an exception is a governance verdict, not a measurement");
        assertEquals(without.totalEntropy(), withExcusal.totalEntropy());
        assertEquals(without.maturity(), withExcusal.maturity());
    }

    @Test
    void anInvariantEveryViolationOfWhichIsExcusedStillDoesNotHold() {
        // Excusal accepts findings; it does not make a constraint true.
        // I_k(S) is 1 whatever the ledger says, which is why E_inv cannot
        // move either.
        ViolatedConstraint constraint = new ViolatedConstraint(
                "no_presentation_to_persistence", "critical", 1, OptionalDouble.of(1.0),
                List.of(new TraceableFinding("invariant:no_presentation_to_persistence",
                        Optional.of(EXCEPTION.target()), Optional.empty(), Optional.of(EXCEPTION))));

        assertFalse(constraint.holds());
        assertEquals(1, constraint.indicatorValue());
        assertEquals(List.of(), constraint.unexcusedViolations());
        assertEquals(1, constraint.violations().size());
    }

    @Test
    void theAggregateIsCarriedWholeAndNeverRenormalized() {
        // Finding M stands: section 6.1 imposes no normalization on
        // lambda_k, so E_inv is unbounded here too. The view reports the
        // very instance it was given - it cannot have divided it by
        // anything.
        InvariantAggregate aggregate = new InvariantAggregate(
                OptionalDouble.of(7.0),
                List.of(new InvariantContribution("budget", 4.0, 1, 4.0),
                        new InvariantContribution("layering", 3.0, 1, 3.0)),
                List.of());
        GovernanceView view = new GovernanceView(
                "subject", Optional.of("fingerprint"), observations(),
                OptionalDouble.of(0.5), OptionalDouble.of(0.5), Optional.of(MaturityLevel.L1_REACTIVE),
                OptionalDouble.of(1.0), Optional.empty(), List.of(), List.of(), aggregate, List.of());

        assertSameInstance(aggregate, view.invariantAggregate());
        assertEquals(OptionalDouble.of(7.0), view.invariantAggregate().value(),
                "unbounded, exactly as increment 29 computed it");
    }

    @Test
    void everyCollectionIsUnmodifiable() {
        GovernanceView view = view(Optional.empty(), List.of());

        assertThrows(UnsupportedOperationException.class, () -> view.observed().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.findings().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.violatedConstraints().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.unmatchedExceptions().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.unanswered().clear());
    }

    @Test
    void aBlankSubjectIdIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new GovernanceView(
                "  ", Optional.empty(), List.of(), OptionalDouble.empty(), OptionalDouble.empty(),
                Optional.empty(), OptionalDouble.empty(), Optional.empty(), List.of(), List.of(),
                InvariantAggregate.undeclared(), List.of()));
    }

    @Test
    void anObservationCannotClaimMoreFindingsThanRelevantSubjects() {
        assertThrows(IllegalArgumentException.class,
                () -> new DimensionObservation("layer", OptionalDouble.of(2.0), OptionalDouble.empty(), 3, 6));
    }

    @Test
    void anIndicatorThatContradictsItsViolationListIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ViolatedConstraint(
                "budget", "warning", 0, OptionalDouble.empty(),
                List.of(new TraceableFinding("invariant:budget",
                        Optional.of(new ExceptionTarget.OfEdge("a", "b", RelationType.CALL)),
                        Optional.empty(), Optional.empty()))));
        assertThrows(IllegalArgumentException.class, () -> new ViolatedConstraint(
                "budget", "warning", 1, OptionalDouble.empty(), List.of()));
    }

    @Test
    void resultIsDeterministicAcrossRepeatedReads() {
        GovernanceView view = view(Optional.empty(), List.of(
                new TraceableFinding("cycle", Optional.empty(), Optional.empty(), Optional.empty())));

        assertEquals(view.unanswered(), view.unanswered());
        assertEquals(view.unexcusedFindings(), view.unexcusedFindings());
    }

    private static GovernanceView view(Optional<BaselineComparison> comparison, List<TraceableFinding> findings) {
        return new GovernanceView(
                "subject", Optional.of("fingerprint"), observations(),
                OptionalDouble.of(0.5), OptionalDouble.of(0.5), Optional.of(MaturityLevel.L1_REACTIVE),
                OptionalDouble.of(1.0), comparison, findings, List.of(),
                InvariantAggregate.undeclared(), List.of());
    }

    private static List<DimensionObservation> observations() {
        return List.of(
                new DimensionObservation("layer", OptionalDouble.of(0.5), OptionalDouble.of(1.0), 2, 1),
                new DimensionObservation("cycle", OptionalDouble.of(0.0), OptionalDouble.of(1.0), 4, 0),
                new DimensionObservation("persistence", OptionalDouble.of(0.5), OptionalDouble.of(1.0), 2, 1),
                new DimensionObservation("security", OptionalDouble.empty(), OptionalDouble.empty(), 0, 0));
    }

    private static void assertSameInstance(Object expected, Object actual) {
        assertTrue(expected == actual, "expected the very same instance, not an equal copy");
    }
}
