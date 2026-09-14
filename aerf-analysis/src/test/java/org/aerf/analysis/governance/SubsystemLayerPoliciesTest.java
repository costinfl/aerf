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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Increment 26 (OQ-04): exactly one subsystem may ever claim a node. */
class SubsystemLayerPoliciesTest {

    @Test
    void duplicateSubsystemNamesAreRejected() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> SubsystemLayerPolicies.of(List.of(
                        subsystem("billing", "com.example.billing"),
                        subsystem("billing", "com.example.shipping"))));

        assertTrue(thrown.getMessage().contains("billing"), thrown.getMessage());
    }

    @Test
    void aPrefixThatWouldAlsoClaimAnothersNodesIsRejectedInEitherDeclarationOrder() {
        // Rejected rather than resolved by most-specific-wins: resolving by
        // prefix length is a derivation, and a typo in one prefix would
        // then silently reassign nodes instead of failing.
        assertThrows(IllegalArgumentException.class, () -> SubsystemLayerPolicies.of(List.of(
                subsystem("outer", "com.example"),
                subsystem("inner", "com.example.billing"))));
        assertThrows(IllegalArgumentException.class, () -> SubsystemLayerPolicies.of(List.of(
                subsystem("inner", "com.example.billing"),
                subsystem("outer", "com.example"))));
    }

    @Test
    void twoSubsystemsSharingOnePrefixAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> SubsystemLayerPolicies.of(List.of(
                subsystem("one", "com.example.billing"),
                subsystem("two", "com.example.billing"))));
    }

    @Test
    void disjointPrefixesAreAccepted() {
        SubsystemLayerPolicies policies = SubsystemLayerPolicies.of(List.of(
                subsystem("billing", "com.example.billing"),
                subsystem("shipping", "com.example.shipping")));

        assertEquals(2, policies.declared().size());
    }

    @Test
    void subsystemsReadBackInTheOrderTheyWereDeclared() {
        SubsystemLayerPolicies policies = SubsystemLayerPolicies.of(List.of(
                subsystem("shipping", "com.example.shipping"),
                subsystem("billing", "com.example.billing")));

        assertEquals(List.of("shipping", "billing"),
                policies.declared().stream().map(SubsystemLayerPolicy::name).toList());
    }

    @Test
    void theGoverningSubsystemIsFoundRegardlessOfDeclarationOrder() {
        NodeId invoice = NodeId.of("com.example.billing.InvoiceService");

        SubsystemLayerPolicies forwards = SubsystemLayerPolicies.of(List.of(
                subsystem("billing", "com.example.billing"), subsystem("shipping", "com.example.shipping")));
        SubsystemLayerPolicies backwards = SubsystemLayerPolicies.of(List.of(
                subsystem("shipping", "com.example.shipping"), subsystem("billing", "com.example.billing")));

        assertEquals(Optional.of("billing"), forwards.governing(invoice).map(SubsystemLayerPolicy::name));
        assertEquals(Optional.of("billing"), backwards.governing(invoice).map(SubsystemLayerPolicy::name));
    }

    @Test
    void aNodeNoSubsystemClaimsHasNoGoverningSubsystem() {
        SubsystemLayerPolicies policies = SubsystemLayerPolicies.of(List.of(
                subsystem("billing", "com.example.billing")));

        assertTrue(policies.governing(NodeId.of("com.example.shipping.CrateService")).isEmpty(),
                "the caller applies the default matrix - it is not this type's job to invent one");
    }

    @Test
    void declaringNoSubsystemsIsTheOrdinaryCaseAndIsNotAnError() {
        assertTrue(SubsystemLayerPolicies.none().isEmpty());
        assertTrue(SubsystemLayerPolicies.of(List.of()).isEmpty());
        assertTrue(SubsystemLayerPolicies.none().governing(NodeId.of("anything")).isEmpty());
    }

    @Test
    void theDeclaredListCannotBeMutatedThroughTheAccessor() {
        SubsystemLayerPolicies policies = SubsystemLayerPolicies.of(List.of(
                subsystem("billing", "com.example.billing")));

        assertThrows(UnsupportedOperationException.class,
                () -> policies.declared().add(subsystem("sneaked", "com.example.other")));
    }

    private static SubsystemLayerPolicy subsystem(String name, String prefix) {
        return new SubsystemLayerPolicy(name, prefix,
                LayerPolicy.of(Set.of(Role.PRESENTATION), Map.of(Role.PRESENTATION, Set.of(Role.PRESENTATION))));
    }
}
