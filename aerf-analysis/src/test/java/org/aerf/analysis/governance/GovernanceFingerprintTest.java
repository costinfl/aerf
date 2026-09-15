package org.aerf.analysis.governance;

import org.aerf.analysis.calibration.CalibrationProfile;
import org.aerf.analysis.calibration.LinearCalibration;
import org.aerf.analysis.calibration.WeightedDimension;
import org.aerf.analysis.invariant.Invariant;
import org.aerf.analysis.invariant.Predicate;
import org.aerf.analysis.invariant.Scope;
import org.aerf.analysis.metrics.layer.LayerPolicy;
import org.aerf.model.RelationType;
import org.aerf.model.Role;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Increment 30 (OQ-14): the fingerprint that lets {@code Risk} refuse
 * across a policy change — and the limit of what it can promise.
 */
class GovernanceFingerprintTest {

    @Test
    void twoIdenticalPoliciesFingerprintIdentically() {
        assertEquals(GovernanceFingerprint.of(policy()), GovernanceFingerprint.of(policy()));
    }

    @Test
    void theSamePolicyFingerprintsIdenticallyAcrossRepeatedCalls() {
        GovernancePolicy governance = policy();

        assertEquals(GovernanceFingerprint.of(governance), GovernanceFingerprint.of(governance));
    }

    @Test
    void changingTheLayerMatrixChangesTheFingerprint() {
        GovernancePolicy relaxed = GovernancePolicy.withOneLayerMatrix(
                LayerPolicy.of(Set.of(Role.PRESENTATION, Role.PERSISTENCE),
                        Map.of(Role.PRESENTATION, Set.of(Role.PRESENTATION, Role.PERSISTENCE))),
                false, profile(), List.of(invariant("rule")));

        assertNotEquals(GovernanceFingerprint.of(policy()), GovernanceFingerprint.of(relaxed));
    }

    @Test
    void changingACalibrationWeightChangesTheFingerprint() {
        GovernancePolicy reweighted = GovernancePolicy.withOneLayerMatrix(
                layerPolicy(), false,
                CalibrationProfile.of(List.of(
                        new WeightedDimension("layer", 0.5, new LinearCalibration()),
                        new WeightedDimension("cycle", 0.5, new LinearCalibration()))),
                List.of(invariant("rule")));

        assertNotEquals(GovernanceFingerprint.of(policy()), GovernanceFingerprint.of(reweighted));
    }

    @Test
    void changingTheSelfCycleChoiceChangesTheFingerprint() {
        assertNotEquals(GovernanceFingerprint.of(policy()),
                GovernanceFingerprint.of(GovernancePolicy.withOneLayerMatrix(
                        layerPolicy(), true, profile(), List.of(invariant("rule")))));
    }

    @Test
    void addingAnInvariantChangesTheFingerprint() {
        assertNotEquals(GovernanceFingerprint.of(policy()),
                GovernanceFingerprint.of(GovernancePolicy.withOneLayerMatrix(
                        layerPolicy(), false, profile(),
                        List.of(invariant("rule"), invariant("another")))));
    }

    @Test
    void changingAnInvariantsWeightChangesTheFingerprint() {
        assertNotEquals(GovernanceFingerprint.of(policy()),
                GovernanceFingerprint.of(withWeights(2.0)));
        assertNotEquals(GovernanceFingerprint.of(withWeights(2.0)),
                GovernanceFingerprint.of(withWeights(3.0)));
    }

    @Test
    void changingTheDriftSensitivityChangesTheFingerprint() {
        assertNotEquals(GovernanceFingerprint.of(policy()),
                GovernanceFingerprint.of(withSensitivity(1.0)));
        assertNotEquals(GovernanceFingerprint.of(withSensitivity(1.0)),
                GovernanceFingerprint.of(withSensitivity(2.0)));
    }

    @Test
    void declaringASubsystemChangesTheFingerprint() {
        GovernancePolicy withSubsystem = new GovernancePolicy(
                layerPolicy(), Subsystems.of(List.of(Subsystem.of("billing", "com.example.billing"))),
                false, profile(), List.of(invariant("rule")), InvariantWeights.none(),
                DriftSensitivity.none(), ApprovedExceptions.none());

        assertNotEquals(GovernanceFingerprint.of(policy()), GovernanceFingerprint.of(withSubsystem));
    }

    @Test
    void approvingAnExceptionChangesTheFingerprint() {
        GovernancePolicy withException = new GovernancePolicy(
                layerPolicy(), Subsystems.none(), false, profile(), List.of(invariant("rule")),
                InvariantWeights.none(), DriftSensitivity.none(),
                ApprovedExceptions.of(List.of(new ApprovedException(
                        new ExceptionTarget.OfEdge("a", "b", RelationType.CALL), "accepted", "alice"))));

        assertNotEquals(GovernanceFingerprint.of(policy()), GovernanceFingerprint.of(withException));
    }

    @Test
    void twoPoliciesDifferingOnlyInAnInvariantsPredicateFingerprintIdentically() {
        // The honest limit, pinned so nobody mistakes this for a proof of
        // sameness. An invariant's predicates cannot be rendered - no
        // Predicate serializer exists, because that is the deliberately
        // unimplemented DSL work - so two policies that assert genuinely
        // different things fingerprint the same.
        //
        // This is exactly why the guarantee is one-directional and why
        // Risk uses it only in the direction where it is sound: differing
        // fingerprints prove the policies differ; matching ones do not
        // prove they are the same.
        Invariant alwaysTrue = new Invariant(
                "rule", Scope.GRAPH, new Predicate.Always(true), new Predicate.Always(true), "critical");
        Invariant alwaysFalse = new Invariant(
                "rule", Scope.GRAPH, new Predicate.Always(true), new Predicate.Always(false), "critical");

        assertEquals(
                GovernanceFingerprint.of(GovernancePolicy.withOneLayerMatrix(
                        layerPolicy(), false, profile(), List.of(alwaysTrue))),
                GovernanceFingerprint.of(GovernancePolicy.withOneLayerMatrix(
                        layerPolicy(), false, profile(), List.of(alwaysFalse))),
                "a real limitation, not an accident - see GovernanceFingerprint's javadoc");
    }

    private static GovernancePolicy policy() {
        return GovernancePolicy.withOneLayerMatrix(
                layerPolicy(), false, profile(), List.of(invariant("rule")));
    }

    private static GovernancePolicy withWeights(double weight) {
        return new GovernancePolicy(layerPolicy(), Subsystems.none(), false, profile(),
                List.of(invariant("rule")),
                InvariantWeights.of(List.of(new WeightedInvariant("rule", weight))),
                DriftSensitivity.none(), ApprovedExceptions.none());
    }

    private static GovernancePolicy withSensitivity(double beta) {
        return new GovernancePolicy(layerPolicy(), Subsystems.none(), false, profile(),
                List.of(invariant("rule")), InvariantWeights.none(),
                DriftSensitivity.of(beta, List.of(new WeightedDriftDimension("layer", 1.0))),
                ApprovedExceptions.none());
    }

    private static LayerPolicy layerPolicy() {
        return LayerPolicy.of(Set.of(Role.PRESENTATION), Map.of(Role.PRESENTATION, Set.of(Role.PRESENTATION)));
    }

    private static CalibrationProfile profile() {
        return CalibrationProfile.of(List.of(new WeightedDimension("layer", 1.0, new LinearCalibration())));
    }

    private static Invariant invariant(String name) {
        return new Invariant(name, Scope.GRAPH, new Predicate.Always(true), new Predicate.Always(true), "critical");
    }
}
