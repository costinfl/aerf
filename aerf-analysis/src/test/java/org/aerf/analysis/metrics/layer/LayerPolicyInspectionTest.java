package org.aerf.analysis.metrics.layer;

import org.aerf.model.Role;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Increment 25 (OQ-02): a governance declaration nobody can read back is
 * not inspectable. See {@code docs/increment-25-governance-policy-boundary.md}.
 */
class LayerPolicyInspectionTest {

    @Test
    void theDeclaredMatrixReadsBackExactlyAsDeclared() {
        LayerPolicy policy = LayerPolicy.of(
                Set.of(Role.PRESENTATION, Role.APPLICATION, Role.PERSISTENCE),
                Map.of(
                        Role.PRESENTATION, Set.of(Role.APPLICATION),
                        Role.APPLICATION, Set.of(Role.PERSISTENCE)));

        assertEquals(Set.of(Role.PRESENTATION, Role.APPLICATION, Role.PERSISTENCE), policy.knownRoles());
        assertEquals(Set.of(Role.APPLICATION), policy.allowedTargets().get(Role.PRESENTATION));
        assertEquals(Set.of(Role.PERSISTENCE), policy.allowedTargets().get(Role.APPLICATION));
    }

    @Test
    void knownRolesReadBackInRoleDeclarationOrderRegardlessOfTheInputSetsOrder() {
        // Role declares PRESENTATION, APPLICATION, DOMAIN, PERSISTENCE,
        // INFRASTRUCTURE, EXTERNAL - so that is the order these come back
        // in, whatever order the caller happened to list them.
        LayerPolicy policy = LayerPolicy.of(
                new java.util.LinkedHashSet<>(List.of(Role.PERSISTENCE, Role.PRESENTATION, Role.DOMAIN)),
                Map.of());

        assertEquals(List.of(Role.PRESENTATION, Role.DOMAIN, Role.PERSISTENCE),
                List.copyOf(policy.knownRoles()));
    }

    @Test
    void allowedTargetsReadBackInRoleDeclarationOrderRegardlessOfTheInputMapsOrder() {
        // The determinism requirement this exists for: every caller in
        // this project builds the matrix with Map.of or Collectors.toMap,
        // neither of which specifies an iteration order - and Map.of
        // randomizes it per JVM invocation by design. Since the matrix now
        // reaches serialized output, inheriting the caller's order would
        // make identical governance emit differently-ordered JSON across
        // runs (section 14).
        Map<Role, Set<Role>> declaredBackwards = new LinkedHashMap<>();
        declaredBackwards.put(Role.PERSISTENCE, Set.of(Role.PERSISTENCE));
        declaredBackwards.put(Role.APPLICATION, Set.of(Role.PERSISTENCE, Role.APPLICATION));
        declaredBackwards.put(Role.PRESENTATION, Set.of(Role.APPLICATION));

        LayerPolicy policy = LayerPolicy.of(
                Set.of(Role.PRESENTATION, Role.APPLICATION, Role.PERSISTENCE), declaredBackwards);

        assertEquals(List.of(Role.PRESENTATION, Role.APPLICATION, Role.PERSISTENCE),
                List.copyOf(policy.allowedTargets().keySet()));
        assertEquals(List.of(Role.APPLICATION, Role.PERSISTENCE),
                List.copyOf(policy.allowedTargets().get(Role.APPLICATION)),
                "the target sets are canonically ordered too, not just the source keys");
    }

    @Test
    void aKnownRoleWithNoDeclaredEntryIsAbsentFromTheMatrixRatherThanMaterializedAsEmpty() {
        LayerPolicy policy = LayerPolicy.of(
                Set.of(Role.PRESENTATION, Role.PERSISTENCE),
                Map.of(Role.PRESENTATION, Set.of(Role.PERSISTENCE)));

        assertTrue(policy.knowsRole(Role.PERSISTENCE), "it is still a role the policy has an opinion about");
        assertFalse(policy.allowedTargets().containsKey(Role.PERSISTENCE),
                "inventing PERSISTENCE -> {} would be deriving a matrix entry nobody declared");
    }

    @Test
    void aPolicyWithNoKnownRolesReadsBackAsAnEmptyMatrixRatherThanThrowing() {
        // EnumSet.copyOf throws IllegalArgumentException on an empty
        // collection, so the empty case has to be built explicitly.
        LayerPolicy policy = LayerPolicy.of(Set.of(), Map.of());

        assertTrue(policy.knownRoles().isEmpty());
        assertTrue(policy.allowedTargets().isEmpty());
    }

    @Test
    void aKnownRoleMayBeDeclaredWithNoTargetsAtAll() {
        LayerPolicy policy = LayerPolicy.of(Set.of(Role.PERSISTENCE), Map.of(Role.PERSISTENCE, Set.of()));

        assertTrue(policy.allowedTargets().get(Role.PERSISTENCE).isEmpty(),
                "declared-but-empty is a real declaration, distinct from not declared at all");
        assertFalse(policy.isAllowed(Role.PERSISTENCE, Role.PERSISTENCE));
    }

    @Test
    void readingTheMatrixBackChangesNeitherKnowsRoleNorIsAllowed() {
        LayerPolicy policy = LayerPolicy.of(
                Set.of(Role.PRESENTATION, Role.APPLICATION, Role.PERSISTENCE),
                Map.of(
                        Role.PRESENTATION, Set.of(Role.PRESENTATION, Role.APPLICATION),
                        Role.APPLICATION, Set.of(Role.APPLICATION, Role.PERSISTENCE),
                        Role.PERSISTENCE, Set.of(Role.PERSISTENCE)));

        for (Role source : Role.values()) {
            assertEquals(policy.knownRoles().contains(source), policy.knowsRole(source));
            for (Role target : Role.values()) {
                boolean fromMatrix = policy.allowedTargets().getOrDefault(source, Set.of()).contains(target);
                assertEquals(fromMatrix, policy.isAllowed(source, target),
                        "the readable matrix and the predicate must never disagree: " + source + " -> " + target);
            }
        }
    }

    @Test
    void theReadableMatrixCannotBeMutatedThroughItsAccessors() {
        LayerPolicy policy = LayerPolicy.of(Set.of(Role.PRESENTATION), Map.of(Role.PRESENTATION, Set.of()));

        assertThrowsUnsupported(() -> policy.knownRoles().add(Role.EXTERNAL));
        assertThrowsUnsupported(() -> policy.allowedTargets().remove(Role.PRESENTATION));
        assertThrowsUnsupported(() -> policy.allowedTargets().get(Role.PRESENTATION).add(Role.EXTERNAL));
    }

    @Test
    void layerPolicyStillOffersNoFactoryThatDerivesAMatrixFromAnOrdering() {
        // The class's central guarantee, made executable: no mechanical
        // derivation from an ordered list of roles matches this
        // framework's own worked examples, so a matrix must be declared.
        // Adding read accessors runs the opposite direction and does not
        // weaken this - but a future List<Role> factory would.
        for (Method method : LayerPolicy.class.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) || !Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            for (Class<?> parameter : method.getParameterTypes()) {
                assertFalse(List.class.isAssignableFrom(parameter),
                        "a public static factory taking a List would let a matrix be derived from an ordering: "
                                + method);
            }
        }
    }

    private static void assertThrowsUnsupported(Runnable mutation) {
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, mutation::run);
    }
}
