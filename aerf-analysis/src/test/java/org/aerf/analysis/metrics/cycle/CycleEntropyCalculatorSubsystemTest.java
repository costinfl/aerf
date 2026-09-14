package org.aerf.analysis.metrics.cycle;

import org.aerf.analysis.governance.Subsystem;
import org.aerf.analysis.governance.Subsystems;
import org.aerf.model.Graph;
import org.aerf.model.Node;
import org.aerf.model.NodeId;
import org.aerf.model.NodeRef;
import org.aerf.model.NodeType;
import org.aerf.model.RelationType;
import org.aerf.model.Role;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Increment 27 (OQ-06): cycle entropy scoped to a declared subsystem.
 * See {@code docs/increment-27-cycle-entropy-scope.md}.
 */
class CycleEntropyCalculatorSubsystemTest {

    private static final Subsystems BILLING_AND_SHIPPING = Subsystems.of(List.of(
            Subsystem.of("billing", "com.example.billing"),
            Subsystem.of("shipping", "com.example.shipping")));

    @Test
    void noDeclaredSubsystemsLeavesTheGlobalMeasurementUntouchedAndReportsNoScopedOnes() {
        Graph graph = billingCyclePlusAcyclicShipping();

        CycleEntropyResult unscoped = CycleEntropyCalculator.withCallAndDependsRelations(false).compute(graph);

        assertEquals(OptionalDouble.of(2.0 / 4.0), unscoped.value());
        assertTrue(unscoped.bySubsystem().isEmpty(),
                "no declaration means no scoped readings - not a reading of zero subsystems' worth");
    }

    @Test
    void theGlobalValueIsUnchangedByAnyDeclaration() {
        // Section 4.2 defines E_C over the whole graph. Scoping adds
        // readings beside it; it never replaces or narrows it.
        Graph graph = billingCyclePlusAcyclicShipping();

        CycleEntropyResult unscoped = CycleEntropyCalculator.withCallAndDependsRelations(false).compute(graph);
        CycleEntropyResult scoped = CycleEntropyCalculator
                .withCallAndDependsRelations(false, BILLING_AND_SHIPPING).compute(graph);

        assertEquals(unscoped.value(), scoped.value());
        assertEquals(unscoped.totalNodeCount(), scoped.totalNodeCount());
        assertEquals(unscoped.participatingNodes(), scoped.participatingNodes());
    }

    @Test
    void sccDetectionSeesTheSameGraphWhateverIsDeclared() {
        // The hard constraint made executable: "do not alter SCC detection
        // merely to answer this question". All scoping happens strictly
        // after detection, so the components found must be identical.
        Graph graph = billingCyclePlusAcyclicShipping();

        assertEquals(
                CycleEntropyCalculator.withCallAndDependsRelations(false).compute(graph).relevantSccs(),
                CycleEntropyCalculator.withCallAndDependsRelations(false, BILLING_AND_SHIPPING)
                        .compute(graph).relevantSccs());
    }

    @Test
    void eachSubsystemIsMeasuredOverItsOwnNodesOnBothSidesOfTheRatio() {
        // Cycle entropy's denominator is the node population itself, so a
        // scoped numerator over a graph-wide denominator would change what
        // the number means rather than narrow it. billing has 2 nodes,
        // both in the cycle; shipping has 2 nodes, neither in a cycle.
        CycleEntropyResult result = CycleEntropyCalculator
                .withCallAndDependsRelations(false, BILLING_AND_SHIPPING)
                .compute(billingCyclePlusAcyclicShipping());

        assertEquals(List.of("billing", "shipping"),
                result.bySubsystem().stream().map(SubsystemCycleEntropy::subsystem).toList());
        assertEquals(OptionalDouble.of(1.0), scoped(result, "billing").value(),
                "both of billing's nodes are in its cycle");
        assertEquals(2, scoped(result, "billing").totalNodeCount());
        assertEquals(OptionalDouble.of(0.0), scoped(result, "shipping").value());
    }

    @Test
    void aSubsystemWithNodesButNoCyclesIsZeroRatherThanUndefined() {
        CycleEntropyResult result = CycleEntropyCalculator
                .withCallAndDependsRelations(false, BILLING_AND_SHIPPING)
                .compute(billingCyclePlusAcyclicShipping());

        assertEquals(OptionalDouble.of(0.0), scoped(result, "shipping").value(),
                "shipping was measured and found acyclic - that is a real result");
    }

    @Test
    void aSubsystemClaimingNoNodesIsUndefinedRatherThanZero() {
        // The distinction that makes the previous test meaningful: "not
        // measurable here" is not the same statement as "measured, and has
        // no cycles". Coercing the first to 0.0 would assert something
        // nothing supports.
        CycleEntropyResult result = CycleEntropyCalculator
                .withCallAndDependsRelations(false, Subsystems.of(List.of(
                        Subsystem.of("billing", "com.example.billing"),
                        Subsystem.of("warehouse", "com.example.warehouse"))))
                .compute(billingCyclePlusAcyclicShipping());

        assertTrue(scoped(result, "warehouse").value().isEmpty(),
                "no node in this graph belongs to warehouse, so it has no reading at all");
        assertEquals(0, scoped(result, "warehouse").totalNodeCount());
    }

    @Test
    void aNodeInNoDeclaredSubsystemCountsGloballyAndInNoSubsystem() {
        CycleEntropyResult result = CycleEntropyCalculator
                .withCallAndDependsRelations(false, Subsystems.of(List.of(
                        Subsystem.of("billing", "com.example.billing"))))
                .compute(billingCyclePlusAcyclicShipping());

        assertEquals(4, result.totalNodeCount(), "the graph-wide denominator still counts every node");
        assertEquals(2, scoped(result, "billing").totalNodeCount(),
                "shipping's two nodes are claimed by nobody and enter no scoped denominator");
    }

    @Test
    void aCycleSpanningTwoSubsystemsContributesItsOwnNodesToEach() {
        // An SCC is a set of nodes and can straddle a boundary, so it is
        // never assigned an owner. Each subsystem counts what it claims.
        CycleEntropyResult result = CycleEntropyCalculator
                .withCallAndDependsRelations(false, BILLING_AND_SHIPPING)
                .compute(cycleAcrossBothSubsystems());

        assertEquals(1, result.relevantSccs().size(), "one SCC, spanning both");
        assertEquals(2, result.relevantSccs().get(0).size());
        assertEquals(OptionalDouble.of(1.0), scoped(result, "billing").value());
        assertEquals(OptionalDouble.of(1.0), scoped(result, "shipping").value());
        assertEquals(Set.of(NodeId.of("com.example.billing.Ledger")),
                scoped(result, "billing").participatingNodes(),
                "billing counts its own node and not its neighbour's");
    }

    @Test
    void subsystemNumeratorsSumToAtMostTheGlobalNumerator() {
        // Equality exactly when every participating node is claimed. Here
        // the cycle is entirely inside billing, so the sum equals it.
        CycleEntropyResult result = CycleEntropyCalculator
                .withCallAndDependsRelations(false, BILLING_AND_SHIPPING)
                .compute(billingCyclePlusAcyclicShipping());

        int scopedTotal = result.bySubsystem().stream()
                .mapToInt(s -> s.participatingNodes().size()).sum();

        assertTrue(scopedTotal <= result.participatingNodes().size(), scopedTotal + " vs " + result.participatingNodes().size());
        assertEquals(result.participatingNodes().size(), scopedTotal,
                "every participating node here is claimed, so nothing is lost");
    }

    @Test
    void anUnclaimedCycleLeavesTheScopedNumeratorsStrictlyBelowTheGlobalOne() {
        CycleEntropyResult result = CycleEntropyCalculator
                .withCallAndDependsRelations(false, Subsystems.of(List.of(
                        Subsystem.of("shipping", "com.example.shipping"))))
                .compute(billingCyclePlusAcyclicShipping());

        int scopedTotal = result.bySubsystem().stream()
                .mapToInt(s -> s.participatingNodes().size()).sum();

        assertEquals(0, scopedTotal);
        assertEquals(2, result.participatingNodes().size(),
                "the billing cycle is still measured graph-wide, it is simply claimed by no subsystem");
    }

    @Test
    void selfCycleGovernanceAppliesGloballyAndPerSubsystemAlike() {
        Graph graph = Graph.builder()
                .addNode(node("com.example.billing.Recursive"))
                .addNode(node("com.example.shipping.Plain"))
                .addEdge(ref("com.example.billing.Recursive"), ref("com.example.billing.Recursive"),
                        RelationType.CALL, List.of())
                .build();

        CycleEntropyResult ungoverned = CycleEntropyCalculator
                .withCallAndDependsRelations(false, BILLING_AND_SHIPPING).compute(graph);
        CycleEntropyResult governed = CycleEntropyCalculator
                .withCallAndDependsRelations(true, BILLING_AND_SHIPPING).compute(graph);

        assertEquals(OptionalDouble.of(0.0), scoped(ungoverned, "billing").value());
        assertEquals(OptionalDouble.of(1.0), scoped(governed, "billing").value(),
                "section 4.2's self-cycle lever is global, and the scoped reading follows it");
    }

    @Test
    void scopedReadingsFollowDeclarationOrder() {
        CycleEntropyResult result = CycleEntropyCalculator
                .withCallAndDependsRelations(false, Subsystems.of(List.of(
                        Subsystem.of("shipping", "com.example.shipping"),
                        Subsystem.of("billing", "com.example.billing"))))
                .compute(billingCyclePlusAcyclicShipping());

        assertEquals(List.of("shipping", "billing"),
                result.bySubsystem().stream().map(SubsystemCycleEntropy::subsystem).toList());
    }

    private static SubsystemCycleEntropy scoped(CycleEntropyResult result, String subsystem) {
        return result.bySubsystem().stream()
                .filter(s -> s.subsystem().equals(subsystem))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no scoped reading for " + subsystem));
    }

    /** A two-node cycle inside billing; shipping has two acyclic nodes. */
    private static Graph billingCyclePlusAcyclicShipping() {
        return Graph.builder()
                .addNode(node("com.example.billing.Invoice"))
                .addNode(node("com.example.billing.Total"))
                .addNode(node("com.example.shipping.Crate"))
                .addNode(node("com.example.shipping.Label"))
                .addEdge(ref("com.example.billing.Invoice"), ref("com.example.billing.Total"),
                        RelationType.DEPENDS, List.of())
                .addEdge(ref("com.example.billing.Total"), ref("com.example.billing.Invoice"),
                        RelationType.DEPENDS, List.of())
                .addEdge(ref("com.example.shipping.Crate"), ref("com.example.shipping.Label"),
                        RelationType.DEPENDS, List.of())
                .build();
    }

    /** One cycle whose two nodes sit in different subsystems. */
    private static Graph cycleAcrossBothSubsystems() {
        return Graph.builder()
                .addNode(node("com.example.billing.Ledger"))
                .addNode(node("com.example.shipping.Crate"))
                .addEdge(ref("com.example.billing.Ledger"), ref("com.example.shipping.Crate"),
                        RelationType.DEPENDS, List.of())
                .addEdge(ref("com.example.shipping.Crate"), ref("com.example.billing.Ledger"),
                        RelationType.DEPENDS, List.of())
                .build();
    }

    private static Node node(String id) {
        return Node.of(NodeId.of(id), NodeType.COMPONENT, Role.UNKNOWN, Map.of(), List.of());
    }

    private static NodeRef ref(String id) {
        return NodeRef.resolved(NodeId.of(id));
    }
}
