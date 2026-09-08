package org.aerf.analysis.metrics.layer;

import org.aerf.model.Role;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A governance-declared layering matrix: which architectural roles may
 * call which other roles. AERF v0.4 section 4.1 requires this matrix to
 * "remain governance-configurable rather than hard-coded as universal
 * architecture" — this class takes that literally and holds only an
 * explicit, caller-supplied matrix.
 *
 * <p>There is deliberately no factory that derives a matrix from an
 * ordered list of roles (e.g. "adjacent layers only" or "any forward
 * call"). Both were tried while implementing this class and both
 * contradict a case v0.4 itself treats as settled: section 4.1's typical
 * ordering (Presentation, Application, Domain, Persistence,
 * Infrastructure) would, under "adjacent only", flag a service calling a
 * repository directly (skipping Domain) as a violation, which is an
 * unremarkable and common pattern; under "any forward call", it would
 * *not* flag Presentation calling Persistence directly, which section
 * 6.3's own worked invariant example (`no_presentation_to_persistence`)
 * treats as a critical violation. Since no single mechanical derivation
 * matches the framework's own examples, the matrix must be declared
 * explicitly rather than guessed at here.
 */
public final class LayerPolicy {

    private final Set<Role> knownRoles;
    private final Map<Role, Set<Role>> allowedTargets;

    private LayerPolicy(Set<Role> knownRoles, Map<Role, Set<Role>> allowedTargets) {
        this.knownRoles = Set.copyOf(Objects.requireNonNull(knownRoles, "knownRoles"));

        Map<Role, Set<Role>> copy = new LinkedHashMap<>();
        for (Map.Entry<Role, Set<Role>> entry : Objects.requireNonNull(allowedTargets, "allowedTargets").entrySet()) {
            Role source = entry.getKey();
            if (!this.knownRoles.contains(source)) {
                throw new IllegalArgumentException("source role " + source + " is not in knownRoles");
            }
            for (Role target : entry.getValue()) {
                if (!this.knownRoles.contains(target)) {
                    throw new IllegalArgumentException(
                            "target role " + target + " (allowed from " + source + ") is not in knownRoles");
                }
            }
            copy.put(source, Set.copyOf(entry.getValue()));
        }
        this.allowedTargets = Collections.unmodifiableMap(copy);
    }

    /**
     * @param knownRoles     every role this policy can judge. A role outside this set is treated as
     *                       not classifiable by this policy (see {@link #knowsRole(Role)}), not as a violation.
     * @param allowedTargets for each source role, the set of roles it may call. A source role with no
     *                       entry is known but permits no calls under this policy.
     */
    public static LayerPolicy of(Set<Role> knownRoles, Map<Role, Set<Role>> allowedTargets) {
        return new LayerPolicy(knownRoles, allowedTargets);
    }

    /** Whether this policy has an opinion about the given role at all. */
    public boolean knowsRole(Role role) {
        return knownRoles.contains(role);
    }

    public boolean isAllowed(Role source, Role target) {
        Set<Role> targets = allowedTargets.get(source);
        return targets != null && targets.contains(target);
    }
}
