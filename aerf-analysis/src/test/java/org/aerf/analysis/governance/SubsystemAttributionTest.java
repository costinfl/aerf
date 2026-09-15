package org.aerf.analysis.governance;

import org.aerf.analysis.metrics.layer.LayerPolicy;
import org.aerf.model.NodeId;
import org.aerf.model.Role;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Increment 31 (OQ-16), resolving finding E: which subsystem's matrix
 * judged a node is now answerable, and is a different question from which
 * subsystem claims it.
 */
class SubsystemAttributionTest {

    private static final LayerPolicy A_MATRIX = LayerPolicy.of(
            Set.of(Role.PRESENTATION), Map.of(Role.PRESENTATION, Set.of(Role.PRESENTATION)));

    @Test
    void aSubsystemWithItsOwnMatrixIsTheOneThatJudgedTheNode() {
        Subsystems subsystems = Subsystems.of(List.of(
                Subsystem.withLayerPolicy("billing", "com.example.billing", A_MATRIX)));

        assertEquals(Optional.of("billing"),
                subsystems.governingMatrix(NodeId.of("com.example.billing.InvoiceService")).map(Subsystem::name));
    }

    @Test
    void aSubsystemWithNoMatrixClaimsTheNodeButDoesNotJudgeIt() {
        // This is the whole reason governingMatrix exists alongside
        // governing. Increment 27 let a subsystem be declared purely to
        // scope cycle entropy, with no layering matrix of its own. Such a
        // node is claimed by that subsystem and judged by the default
        // matrix, so naming the claimant as the judge would credit a
        // declaration that had no part in the verdict.
        Subsystems subsystems = Subsystems.of(List.of(
                Subsystem.of("billing", "com.example.billing")));
        NodeId invoice = NodeId.of("com.example.billing.InvoiceService");

        assertEquals(Optional.of("billing"), subsystems.governing(invoice).map(Subsystem::name),
                "the subsystem still claims the node - cycle entropy scopes by exactly this");
        assertTrue(subsystems.governingMatrix(invoice).isEmpty(),
                "but the default matrix judged it, so no subsystem may be named as the judge");
    }

    @Test
    void aNodeNoSubsystemClaimsIsJudgedByTheDefaultMatrix() {
        Subsystems subsystems = Subsystems.of(List.of(
                Subsystem.withLayerPolicy("billing", "com.example.billing", A_MATRIX)));

        assertTrue(subsystems.governingMatrix(NodeId.of("com.example.shipping.CrateService")).isEmpty());
    }

    @Test
    void declaringNoSubsystemsLeavesEveryNodeJudgedByTheDefaultMatrix() {
        assertTrue(Subsystems.none().governingMatrix(NodeId.of("anything")).isEmpty());
    }

    @Test
    void theJudgingSubsystemIsIndependentOfDeclarationOrder() {
        NodeId invoice = NodeId.of("com.example.billing.InvoiceService");
        Subsystem billing = Subsystem.withLayerPolicy("billing", "com.example.billing", A_MATRIX);
        Subsystem shipping = Subsystem.withLayerPolicy("shipping", "com.example.shipping", A_MATRIX);

        assertEquals(Optional.of("billing"),
                Subsystems.of(List.of(billing, shipping)).governingMatrix(invoice).map(Subsystem::name));
        assertEquals(Optional.of("billing"),
                Subsystems.of(List.of(shipping, billing)).governingMatrix(invoice).map(Subsystem::name));
    }

    @Test
    void governingMatrixSelectsExactlyWhatTheCalculatorUsedToSelectInline() {
        // A behaviour-identity check for the extraction itself.
        // LayerEntropyCalculator previously inlined
        // governing(id).flatMap(Subsystem::layerPolicy); it now delegates
        // here, so the two must agree on every shape of declaration - a
        // subsystem with a matrix, one without, and an unclaimed node.
        Subsystems subsystems = Subsystems.of(List.of(
                Subsystem.withLayerPolicy("billing", "com.example.billing", A_MATRIX),
                Subsystem.of("shipping", "com.example.shipping")));

        for (String id : List.of("com.example.billing.InvoiceService",
                "com.example.shipping.CrateService",
                "com.example.shared.Clock")) {
            NodeId nodeId = NodeId.of(id);
            assertEquals(
                    subsystems.governing(nodeId).flatMap(Subsystem::layerPolicy),
                    subsystems.governingMatrix(nodeId).flatMap(Subsystem::layerPolicy),
                    "the extracted rule must choose the same matrix the inline one did, for " + id);
        }
    }
}
