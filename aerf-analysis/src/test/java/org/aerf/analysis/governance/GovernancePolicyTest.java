package org.aerf.analysis.governance;

import org.aerf.analysis.calibration.CalibrationProfile;
import org.aerf.analysis.calibration.LinearCalibration;
import org.aerf.analysis.calibration.WeightedDimension;
import org.aerf.analysis.invariant.Invariant;
import org.aerf.analysis.invariant.Predicate;
import org.aerf.analysis.invariant.Scope;
import org.aerf.analysis.metrics.layer.LayerPolicy;
import org.aerf.model.Role;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Increment 25 (OQ-02): governance input represented explicitly, with no
 * hidden default. See {@code docs/increment-25-governance-policy-boundary.md}.
 */
class GovernancePolicyTest {

    @Test
    void everyGovernanceComponentIsRequiredSoNoPolicyCanBeBuiltByOmission() {
        // "No hidden default governance policy is introduced" (OQ-02's
        // acceptance) enforced by the compiler and these checks rather
        // than by documentation: there is nowhere for a default to hide.
        Subsystems none = Subsystems.none();
        ApprovedExceptions noExceptions = ApprovedExceptions.none();
        InvariantWeights noWeights = InvariantWeights.none();
        DriftSensitivity noDrift = DriftSensitivity.none();
        assertThrows(NullPointerException.class,
                () -> new GovernancePolicy(null, none, false, profile(), List.of(), noWeights, noDrift, noExceptions));
        assertThrows(NullPointerException.class,
                () -> new GovernancePolicy(policy(), null, false, profile(), List.of(), noWeights, noDrift, noExceptions));
        assertThrows(NullPointerException.class,
                () -> new GovernancePolicy(policy(), none, false, null, List.of(), noWeights, noDrift, noExceptions));
        assertThrows(NullPointerException.class,
                () -> new GovernancePolicy(policy(), none, false, profile(), null, noWeights, noDrift, noExceptions));
        assertThrows(NullPointerException.class,
                () -> new GovernancePolicy(policy(), none, false, profile(), List.of(), null, noDrift, noExceptions));
        assertThrows(NullPointerException.class,
                () -> new GovernancePolicy(policy(), none, false, profile(), List.of(), noWeights, null, noExceptions));
        assertThrows(NullPointerException.class,
                () -> new GovernancePolicy(policy(), none, false, profile(), List.of(), noWeights, noDrift, null));
    }

    @Test
    void anOrganizationDeclaringNoInvariantsMustSaySoWithAnEmptyListRatherThanOmittingThem() {
        GovernancePolicy governance = GovernancePolicy.withOneLayerMatrix(policy(), false, profile(), List.of());

        assertTrue(governance.invariants().isEmpty(),
                "declaring no invariants is a legitimate governance position - but it has to be declared");
    }

    @Test
    void invariantsReadBackInTheOrderTheyWereDeclared() {
        Invariant first = invariant("a_first");
        Invariant second = invariant("b_second");

        GovernancePolicy governance =
                GovernancePolicy.withOneLayerMatrix(policy(), false, profile(), List.of(second, first));

        assertEquals(List.of("b_second", "a_first"),
                governance.invariants().stream().map(Invariant::name).toList(),
                "declaration order is the organization's own and is not re-sorted");
    }

    @Test
    void theDeclaredInvariantListCannotBeMutatedThroughTheAccessor() {
        GovernancePolicy governance =
                GovernancePolicy.withOneLayerMatrix(policy(), false, profile(), List.of(invariant("only")));

        assertThrows(UnsupportedOperationException.class, () -> governance.invariants().add(invariant("sneaked_in")));
    }

    @Test
    void theSelfCycleChoiceIsCarriedVerbatimInBothDirections() {
        assertEquals(false, GovernancePolicy.withOneLayerMatrix(policy(), false, profile(), List.of())
                .includeSelfCyclesInCycleEntropy());
        assertEquals(true, GovernancePolicy.withOneLayerMatrix(policy(), true, profile(), List.of())
                .includeSelfCyclesInCycleEntropy());
    }

    @Test
    void theSameDeclarationsBuiltTwiceProduceTwoPoliciesThatAgreeOnEveryReadableDetail() {
        // Deliberately not an equals() comparison: LayerPolicy,
        // CalibrationProfile and most CalibrationFunction/Predicate
        // implementations define none, so equals here would compare
        // identities and pass vacuously.
        GovernancePolicy one = GovernancePolicy.withOneLayerMatrix(policy(), false, profile(), List.of(invariant("budget")));
        GovernancePolicy other = GovernancePolicy.withOneLayerMatrix(policy(), false, profile(), List.of(invariant("budget")));

        assertEquals(one.layerPolicy().knownRoles(), other.layerPolicy().knownRoles());
        assertEquals(one.layerPolicy().allowedTargets(), other.layerPolicy().allowedTargets());
        assertEquals(one.includeSelfCyclesInCycleEntropy(), other.includeSelfCyclesInCycleEntropy());
        assertEquals(
                one.calibrationProfile().dimensions().stream().map(WeightedDimension::name).toList(),
                other.calibrationProfile().dimensions().stream().map(WeightedDimension::name).toList());
        assertEquals(
                one.invariants().stream().map(Invariant::name).toList(),
                other.invariants().stream().map(Invariant::name).toList());
    }

    private static LayerPolicy policy() {
        return LayerPolicy.of(Set.of(Role.PRESENTATION), Map.of(Role.PRESENTATION, Set.of(Role.PRESENTATION)));
    }

    private static CalibrationProfile profile() {
        return CalibrationProfile.of(List.of(new WeightedDimension("layer", 1.0, new LinearCalibration())));
    }

    private static Invariant invariant(String name) {
        return new Invariant(name, Scope.GRAPH, new Predicate.Always(true), new Predicate.Always(true), "critical");
    }
}
