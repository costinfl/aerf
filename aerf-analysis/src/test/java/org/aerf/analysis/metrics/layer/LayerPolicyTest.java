package org.aerf.analysis.metrics.layer;

import org.aerf.model.Role;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayerPolicyTest {

    @Test
    void isAllowedReflectsTheDeclaredMatrixExactly() {
        LayerPolicy policy = LayerPolicy.of(
                Set.of(Role.PRESENTATION, Role.APPLICATION, Role.PERSISTENCE),
                Map.of(
                        Role.PRESENTATION, Set.of(Role.APPLICATION),
                        Role.APPLICATION, Set.of(Role.PERSISTENCE)));

        assertTrue(policy.isAllowed(Role.PRESENTATION, Role.APPLICATION));
        assertTrue(policy.isAllowed(Role.APPLICATION, Role.PERSISTENCE));
        assertFalse(policy.isAllowed(Role.PRESENTATION, Role.PERSISTENCE), "not declared, so not allowed");
        assertFalse(policy.isAllowed(Role.PERSISTENCE, Role.APPLICATION), "no entry at all for Persistence as a source");
    }

    @Test
    void knowsRoleReflectsOnlyTheDeclaredRoleVocabulary() {
        LayerPolicy policy = LayerPolicy.of(Set.of(Role.PRESENTATION, Role.APPLICATION), Map.of());

        assertTrue(policy.knowsRole(Role.PRESENTATION));
        assertFalse(policy.knowsRole(Role.PERSISTENCE), "Persistence was never declared as part of this policy");
        assertFalse(policy.knowsRole(Role.UNKNOWN));
    }

    @Test
    void aSourceRoleUsedInTheMatrixMustBeInKnownRoles() {
        assertThrows(IllegalArgumentException.class, () -> LayerPolicy.of(
                Set.of(Role.APPLICATION),
                Map.of(Role.PRESENTATION, Set.of(Role.APPLICATION))));
    }

    @Test
    void aTargetRoleUsedInTheMatrixMustBeInKnownRoles() {
        assertThrows(IllegalArgumentException.class, () -> LayerPolicy.of(
                Set.of(Role.PRESENTATION),
                Map.of(Role.PRESENTATION, Set.of(Role.APPLICATION))));
    }
}
