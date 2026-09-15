package org.aerf.report;

import org.aerf.analysis.calibration.CalibrationProfile;
import org.aerf.analysis.calibration.LinearCalibration;
import org.aerf.analysis.calibration.WeightedDimension;
import org.aerf.analysis.governance.ApprovedExceptions;
import org.aerf.analysis.governance.InvariantWeights;
import org.aerf.analysis.governance.GovernancePolicy;
import org.aerf.analysis.governance.Subsystems;
import org.aerf.analysis.governance.Subsystem;
import org.aerf.analysis.invariant.Invariant;
import org.aerf.analysis.invariant.Predicate;
import org.aerf.analysis.invariant.Scope;
import org.aerf.analysis.invariant.examples.SpecWorkedExamples;
import org.aerf.analysis.metrics.layer.LayerPolicy;
import org.aerf.model.Role;
import org.aerf.report.json.JsonWriter;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Increment 25 (OQ-02): a report says what it was measured against, not
 * only what it measured.
 */
class GovernanceJsonTest {

    @Test
    void theLayerMatrixSerializesInRoleDeclarationOrderSoIdenticalPoliciesProduceIdenticalJson() {
        // Declared back-to-front; must serialize front-to-back, because
        // Map.of randomizes its iteration order per JVM invocation and
        // section 14 requires identical sources to produce identical
        // output across runs, not just within one.
        Map<Role, Set<Role>> backwards = new LinkedHashMap<>();
        backwards.put(Role.PERSISTENCE, Set.of(Role.PERSISTENCE));
        backwards.put(Role.PRESENTATION, Set.of(Role.APPLICATION, Role.PRESENTATION));

        String json = JsonWriter.write(GovernanceJson.policy(governance(
                LayerPolicy.of(Set.of(Role.PRESENTATION, Role.APPLICATION, Role.PERSISTENCE), backwards))));

        assertTrue(json.contains("\"knownRoles\":[\"PRESENTATION\",\"APPLICATION\",\"PERSISTENCE\"]"), json);
        assertTrue(json.contains(
                "\"allowedTargets\":{\"PRESENTATION\":[\"PRESENTATION\",\"APPLICATION\"],"
                        + "\"PERSISTENCE\":[\"PERSISTENCE\"]}"), json);
    }

    @Test
    void aKnownRoleWithNoDeclaredTargetsDoesNotAppearAsAnEmptyArray() {
        String json = JsonWriter.write(GovernanceJson.policy(governance(LayerPolicy.of(
                Set.of(Role.PRESENTATION, Role.EXTERNAL),
                Map.of(Role.PRESENTATION, Set.of(Role.EXTERNAL))))));

        assertTrue(json.contains("\"knownRoles\":[\"PRESENTATION\",\"EXTERNAL\"]"), json);
        assertFalse(json.contains("\"EXTERNAL\":[]"),
                "EXTERNAL is known but was given no entry; emitting one would report a declaration nobody made");
    }

    @Test
    void theSelfCyclePolicySerializesAsAnExplicitBooleanRatherThanBeingOmittedWhenFalse() {
        assertTrue(JsonWriter.write(GovernanceJson.policy(
                        GovernancePolicy.withOneLayerMatrix(policy(), false, profile(), List.of())))
                .contains("\"includeSelfCyclesInCycleEntropy\":false"));
        assertTrue(JsonWriter.write(GovernanceJson.policy(
                        GovernancePolicy.withOneLayerMatrix(policy(), true, profile(), List.of())))
                .contains("\"includeSelfCyclesInCycleEntropy\":true"));
    }

    @Test
    void aZeroWeightedCalibrationDimensionIsSerializedRatherThanDropped() {
        // Weighting security out is a documented governance decision about
        // a current capability boundary, not the absence of a decision.
        CalibrationProfile withSecurityAtZero = CalibrationProfile.of(List.of(
                new WeightedDimension("layer", 1.0, new LinearCalibration()),
                new WeightedDimension("security", 0.0, new LinearCalibration())));

        String json = JsonWriter.write(GovernanceJson.policy(
                GovernancePolicy.withOneLayerMatrix(policy(), false, withSecurityAtZero, List.of())));

        assertTrue(json.contains(
                "\"calibration\":[{\"dimension\":\"layer\",\"weight\":1.0,\"function\":\"linear\"},"
                        + "{\"dimension\":\"security\",\"weight\":0.0,\"function\":\"linear\"}]"), json);
    }

    @Test
    void invariantsSerializeTheirNameScopeSeverityAndReferencedMetricsOnly() {
        String json = JsonWriter.write(GovernanceJson.policy(GovernancePolicy.withOneLayerMatrix(
                policy(), false, profile(),
                List.of(SpecWorkedExamples.noPresentationToPersistence(),
                        SpecWorkedExamples.entropyBudget(0.35)))));

        assertTrue(json.contains(
                "\"invariants\":[{\"name\":\"no_presentation_to_persistence\",\"scope\":\"EDGE\","
                        + "\"severity\":\"critical\",\"referencedMetrics\":[]},"
                        + "{\"name\":\"entropy_budget\",\"scope\":\"GRAPH\",\"severity\":\"critical\","
                        + "\"referencedMetrics\":[\"total_entropy\"]}]"), json);
    }

    @Test
    void noPredicateIsSerializedBecauseNoPredicateSerializerExistsYet() {
        // This output is explicitly partial and says so: an invariant's
        // conditions are not emitted, because writing them would mean
        // designing the textual form of the invariant DSL, which AERF
        // v0.4 deliberately leaves unimplemented. Recorded as partial
        // rather than presented as complete - and this fails the moment
        // someone smuggles a predicate in, which is when that gap has
        // been closed and needs its own decision record.
        Invariant invariant = new Invariant(
                "a_named_rule", Scope.GRAPH, new Predicate.Always(true), new Predicate.Always(true), "critical");

        String json = JsonWriter.write(GovernanceJson.policy(
                GovernancePolicy.withOneLayerMatrix(policy(), false, profile(), List.of(invariant))));

        assertTrue(json.contains("\"name\":\"a_named_rule\""), json);
        assertFalse(json.toLowerCase(java.util.Locale.ROOT).contains("always"), json);
        assertFalse(json.contains("when"), json);
        assertFalse(json.contains("assert"), json);
    }

    @Test
    void detectionCatalogsAreNotSerializedUnderGovernanceBecauseTheyAreNotGovernance() {
        // The boundary made visible: the key is named governance and
        // contains only governance. Seed, refinement and security rules
        // are technology knowledge, authored by whoever maintains the
        // adapter - so a report is not fully reproducible from this key
        // alone, which is recorded as a named limitation.
        String json = JsonWriter.write(GovernanceJson.policy(governance(policy())));

        assertFalse(json.contains("seedRules"), json);
        assertFalse(json.contains("refinementRules"), json);
        assertFalse(json.contains("securityRules"), json);
    }

    @Test
    void theSameGovernanceSerializesToTheSameBytesEveryTime() {
        GovernancePolicy governance = governance(policy());

        assertEquals(JsonWriter.write(GovernanceJson.policy(governance)),
                JsonWriter.write(GovernanceJson.policy(governance)));
    }

    private static GovernancePolicy governance(LayerPolicy layerPolicy) {
        return GovernancePolicy.withOneLayerMatrix(layerPolicy, false, profile(), List.of());
    }

    private static LayerPolicy policy() {
        return LayerPolicy.of(Set.of(Role.PRESENTATION), Map.of(Role.PRESENTATION, Set.of(Role.PRESENTATION)));
    }

    private static CalibrationProfile profile() {
        return CalibrationProfile.of(List.of(new WeightedDimension("layer", 1.0, new LinearCalibration())));
    }

    @Test
    void declaringNoSubsystemsEmitsAnEmptyArrayRatherThanOmittingTheKey() {
        // Choosing one matrix for the whole graph is a governance decision,
        // not the absence of one - the same reason a zero-weighted
        // dimension is serialized rather than dropped.
        assertTrue(JsonWriter.write(GovernanceJson.policy(governance(policy())))
                .contains("\"subsystems\":[]"));
    }

    @Test
    void subsystemsSerializeInDeclarationOrderWithTheirOwnFullMatrices() {
        LayerPolicy legacy = LayerPolicy.of(
                Set.of(Role.PRESENTATION, Role.PERSISTENCE),
                Map.of(Role.PRESENTATION, Set.of(Role.PERSISTENCE)));

        String json = JsonWriter.write(GovernanceJson.policy(new GovernancePolicy(
                policy(),
                Subsystems.of(List.of(
                        Subsystem.withLayerPolicy("shipping", "com.example.shipping", legacy),
                        Subsystem.withLayerPolicy("billing", "com.example.billing", policy()))),
                false, profile(), List.of(), InvariantWeights.none(), ApprovedExceptions.none())));

        assertTrue(json.contains(
                "\"subsystems\":["
                        + "{\"name\":\"shipping\",\"idPrefix\":\"com.example.shipping\","
                        + "\"layerPolicy\":{\"knownRoles\":[\"PRESENTATION\",\"PERSISTENCE\"],"
                        + "\"allowedTargets\":{\"PRESENTATION\":[\"PERSISTENCE\"]}}},"
                        + "{\"name\":\"billing\",\"idPrefix\":\"com.example.billing\","
                        + "\"layerPolicy\":{\"knownRoles\":[\"PRESENTATION\"],"
                        + "\"allowedTargets\":{\"PRESENTATION\":[\"PRESENTATION\"]}}}]"), json);
    }

    @Test
    void aSubsystemDeclarationIsFullyRecoverableFromTheReport() {
        // The inspectability criterion applied to OQ-04: a reader can see
        // which subsystems were declared, what each one claimed, and what
        // each one permitted - so a per-subsystem measurement can be
        // understood without the config that produced it.
        String json = JsonWriter.write(GovernanceJson.policy(new GovernancePolicy(
                policy(),
                Subsystems.of(List.of(
                        Subsystem.withLayerPolicy("billing", "com.example.billing", policy()))),
                false, profile(), List.of(), InvariantWeights.none(), ApprovedExceptions.none())));

        assertTrue(json.contains("\"name\":\"billing\""), json);
        assertTrue(json.contains("\"idPrefix\":\"com.example.billing\""), json);
    }

    @Test
    void aSubsystemWithNoMatrixSerializesLayerPolicyAsNull() {
        // Increment 27: a subsystem may scope the cycle measurement while
        // declaring no matrix of its own, in which case the default one
        // judges its nodes. Null, not an empty matrix - those mean
        // different things.
        String json = JsonWriter.write(GovernanceJson.policy(new GovernancePolicy(
                policy(),
                Subsystems.of(List.of(Subsystem.of("billing", "com.example.billing"))),
                false, profile(), List.of(), InvariantWeights.none(), ApprovedExceptions.none())));

        assertTrue(json.contains(
                "\"subsystems\":[{\"name\":\"billing\",\"idPrefix\":\"com.example.billing\","
                        + "\"layerPolicy\":null}]"), json);
    }
}
