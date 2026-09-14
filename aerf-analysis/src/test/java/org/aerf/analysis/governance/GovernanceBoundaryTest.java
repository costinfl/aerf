package org.aerf.analysis.governance;

import org.aerf.analysis.metrics.security.SecurityOpportunityRule;
import org.aerf.analysis.role.RolePrecedence;
import org.aerf.analysis.role.RoleSignal;
import org.aerf.model.RelationType;
import org.aerf.model.Role;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The negative half of Increment 25 (OQ-02): what {@link GovernancePolicy}
 * deliberately does <em>not</em> carry, and why.
 *
 * <p>Each test here pins a decision that was recorded rather than
 * implemented. They are written to fail the day someone widens the
 * governance surface, so that widening happens through a decision record
 * rather than by accident — the same technique Increment 23 used for the
 * unreachable half of OQ-11. Failing one of these is not necessarily a
 * bug; it means the corresponding open question has been answered and its
 * record needs writing.
 */
class GovernanceBoundaryTest {

    @Test
    void governanceDeclaresNoRoleAssignmentSoSection33sGovernanceEvidenceClassRemainsUnimplemented() {
        // The original OQ-02 (open-questions-register entry 2) asks how an
        // organization declares or overrides a node's role outright -
        // section 3.3's fourth evidence class, alongside Structural,
        // Semantic and Graph. It is still unimplemented, and Increment 25
        // records why rather than implementing it: see the two structural
        // obstacles pinned by the next two tests.
        for (RecordComponent component : GovernancePolicy.class.getRecordComponents()) {
            String type = component.getGenericType().getTypeName();
            assertFalse(type.contains(Role.class.getName()),
                    "a role-typed governance component would be section 3.3's Governance evidence class arriving "
                            + "without a decision record: " + component);
        }
    }

    @Test
    void roleConflictsAreStillResolvedByRolePrecedenceAloneWithNoNotionOfAuthorship() {
        // The first obstacle, and the reason governance role assignment
        // cannot simply be another RoleInferenceRule: RolePrecedence
        // resolves competing signals purely by section 3.4's role order.
        // A governance declaration entering that way would *lose* to a
        // naming heuristic whenever the heuristic's role outranks it.
        RoleSignal governanceWouldDeclare =
                new RoleSignal(Role.PRESENTATION, "governance", "the organization says this is a UI component");
        RoleSignal aNamingHeuristicGuessed =
                new RoleSignal(Role.PERSISTENCE, "name-ends-with-repository", "name ends with Repository");

        assertEquals(Role.PERSISTENCE,
                RolePrecedence.winner(List.of(governanceWouldDeclare, aNamingHeuristicGuessed)),
                "a heuristic outranks the organization's own declaration today - which is exactly why "
                        + "OQ-02's role-assignment half needs its own decision, not a new rule");
    }

    @Test
    void evidenceStillModelsOnlyWhatWasObservedSoADeclarationHasNoNaturalPlaceInIt() {
        // The second obstacle. Evidence's own contract is that it "always
        // represents something that was actually observed", which a
        // governance declaration is not. Representing one would mean
        // either relaxing that contract or carrying declarations outside
        // the evidence chain - a modelling decision, not a config change.
        for (RecordComponent component : GovernancePolicy.class.getRecordComponents()) {
            assertFalse(component.getGenericType().getTypeName()
                            .contains(org.aerf.model.Evidence.class.getName()),
                    "governance does not manufacture evidence: " + component);
        }
    }

    @Test
    void thereIsExactlyOneSubsystemDeclarationServingBothScopedDimensions() {
        // Increment 25 pinned that no scope-shaped component existed at
        // all, because OQ-04 and OQ-06 both needed one and neither could
        // begin until it was decided how a subsystem is identified.
        // Increment 26 answered that for OQ-04 and Increment 27 for OQ-06,
        // so the pin narrows again rather than disappearing: there is one
        // subsystems component, not one per dimension. Two lists could put
        // the same node in different subsystems depending on which metric
        // was asking.
        assertEquals(
                List.of("layerPolicy", "subsystems", "includeSelfCyclesInCycleEntropy",
                        "calibrationProfile", "invariants"),
                Arrays.stream(GovernancePolicy.class.getRecordComponents())
                        .map(RecordComponent::getName).toList());
    }

    @Test
    void governanceCannotDeclareACycleIrrelevantSoOq06sToleranceHalfRemainsOpen() {
        // Increment 27 answered OQ-06's measurement half: cycle entropy is
        // now reported per declared subsystem beside the graph-wide value.
        // This pin narrows to the half it did NOT answer - the original
        // register's motivation, "tolerate cycles within one module while
        // forbidding them across module boundaries". That is a relevance
        // policy, not a scope: it would change section 4.2's own
        // definition of which SCCs count rather than extend governance
        // configuration, and no repository in this project's evidence base
        // contains a cycle at all to calibrate it against.
        //
        // Section 4.2's one named lever on cycle relevance is the
        // self-cycle choice, and it stays a single global boolean.
        RecordComponent selfCycleChoice = Arrays.stream(GovernancePolicy.class.getRecordComponents())
                .filter(c -> c.getName().equals("includeSelfCyclesInCycleEntropy"))
                .findFirst()
                .orElseThrow();

        assertEquals(boolean.class, selfCycleChoice.getType(),
                "a per-subsystem self-cycle policy would be a second relevance lever, undecided");
        for (RecordComponent component : GovernancePolicy.class.getRecordComponents()) {
            String name = component.getName().toLowerCase(Locale.ROOT);
            assertFalse(name.contains("tolerat") || name.contains("cycleRelevance".toLowerCase(Locale.ROOT)),
                    "a cycle-tolerance policy means OQ-06's second half was answered without a record: "
                            + component);
        }
    }

    @Test
    void scopingCycleEntropyDidNotMakeTheGraphWideMeasurementOptional() {
        // Section 4.2 defines E_C over the whole graph. Increment 27 adds
        // readings beside it and must never replace it, so CycleEntropyResult
        // keeps a non-optional graph-wide value.
        assertEquals(int.class,
                Arrays.stream(org.aerf.analysis.metrics.cycle.CycleEntropyResult.class.getRecordComponents())
                        .filter(c -> c.getName().equals("totalNodeCount"))
                        .findFirst().orElseThrow().getType(),
                "the graph-wide denominator is still every node in the graph");
    }

    @Test
    void relationScopeIsNotPartOfTheGovernancePolicySurface() {
        // Which relations a dimension measures over is measurement
        // definition fixed by section 4, not an organizational input:
        // section 6.3's own worked example fixes [CALL, DEPENDS], and
        // Pipeline composes the calculators from their public factories.
        // Recorded as a finding and handed to OQ-06, which already owns
        // "what is this measured over" - and deferred deliberately,
        // because DimensionConfidence reads the same set, so one wrong
        // plumb would move layer entropy and three confidences at once.
        for (RecordComponent component : GovernancePolicy.class.getRecordComponents()) {
            assertFalse(component.getGenericType().getTypeName().contains(RelationType.class.getName()),
                    "relation scope became governance without a decision record: " + component);
        }
    }

    @Test
    void governanceSelectsNoSecurityConcernsSoConcernScopeStaysFusedIntoTheDetectionCatalog() {
        // Which code pattern signals a concern is engineering; which
        // concerns an organization cares about is arguably governance.
        // One interface does both today. Separating them would filter the
        // security opportunity denominator, making it a policy input under
        // the measurement-change checklist - recorded, not done here.
        for (RecordComponent component : GovernancePolicy.class.getRecordComponents()) {
            String type = component.getGenericType().getTypeName();
            assertFalse(type.contains(SecurityOpportunityRule.class.getName()) || type.contains("SecurityConcern"),
                    "concern selection moved to governance without a decision record: " + component);
        }
    }
}
