package org.aerf.analysis.governance;

import org.aerf.analysis.metrics.layer.LayerPolicy;
import org.aerf.model.NodeId;
import org.aerf.model.Role;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Increment 26 (OQ-04): a subsystem is governance-declared, and its
 * membership test never interprets a node id.
 */
class SubsystemTest {

    @Test
    void everyPartOfTheDeclarationIsRequired() {
        assertThrows(NullPointerException.class, () -> Subsystem.withLayerPolicy(null, "com.example", policy()));
        assertThrows(NullPointerException.class, () -> Subsystem.withLayerPolicy("billing", null, policy()));
        assertThrows(NullPointerException.class, () -> Subsystem.withLayerPolicy("billing", "com.example", null));
        assertThrows(IllegalArgumentException.class, () -> Subsystem.withLayerPolicy(" ", "com.example", policy()));
    }

    @Test
    void aBlankPrefixIsRejectedBecauseItWouldSilentlyClaimEveryNode() {
        // An empty prefix matches every id, making this subsystem's matrix
        // the universal one and shadowing the default without saying so.
        assertThrows(IllegalArgumentException.class, () -> Subsystem.withLayerPolicy("everything", "", policy()));
    }

    @Test
    void membershipIsAPlainPrefixTestOverTheOpaqueId() {
        // NodeId is documented as "an opaque, stable, technology-derived
        // string" that AERF does not interpret. startsWith is a total
        // operation on any string - nothing is parsed, split or given
        // meaning here. The prefix's meaning is the declaring
        // organization's, not AERF's.
        Subsystem billing = Subsystem.withLayerPolicy("billing", "com.example.billing", policy());

        assertTrue(billing.matches(NodeId.of("com.example.billing.InvoiceService")));
        assertFalse(billing.matches(NodeId.of("com.example.shipping.CrateService")));
        assertFalse(billing.matches(NodeId.of("org.example.billing.InvoiceService")));
    }

    @Test
    void aMethodIsClaimedByItsDeclaringTypesSubsystem() {
        // JavaNodeIds mints a method id as Owner#name(params), so a method
        // id starts with its declaring type's id - which is why declaring a
        // package prefix picks up that package's methods too, without this
        // class knowing anything about Java.
        Subsystem billing = Subsystem.withLayerPolicy("billing", "com.example.billing", policy());

        assertTrue(billing.matches(NodeId.of("com.example.billing.InvoiceService#total(java.lang.Long)")));
    }

    @Test
    void aPrefixMatchesTheIdItIsIdenticalTo() {
        Subsystem exact = Subsystem.withLayerPolicy("one-node", "com.example.Lonely", policy());

        assertTrue(exact.matches(NodeId.of("com.example.Lonely")));
    }

    private static LayerPolicy policy() {
        return LayerPolicy.of(Set.of(Role.PRESENTATION), Map.of(Role.PRESENTATION, Set.of(Role.PRESENTATION)));
    }

    @Test
    void aSubsystemMayBeDeclaredWithoutALayerMatrixOfItsOwn() {
        // Increment 27: cycle entropy is a second consumer and needs the
        // identity without a matrix, so an organization scoping cycles is
        // not forced to invent a layering matrix it does not want.
        Subsystem identityOnly = Subsystem.of("billing", "com.example.billing");

        assertTrue(identityOnly.layerPolicy().isEmpty());
        assertTrue(identityOnly.matches(NodeId.of("com.example.billing.InvoiceService")),
                "it still claims nodes - that is what scopes the cycle measurement");
    }

    @Test
    void aSubsystemWithAMatrixCarriesItAndRejectsANullOne() {
        Subsystem withMatrix = Subsystem.withLayerPolicy("billing", "com.example.billing", policy());

        assertTrue(withMatrix.layerPolicy().isPresent());
        assertThrows(NullPointerException.class,
                () -> Subsystem.withLayerPolicy("billing", "com.example.billing", null));
    }
}
