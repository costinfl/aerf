package org.aerf.analysis.metrics.layer;

import org.aerf.model.Role;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
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
        this.knownRoles = canonical(Objects.requireNonNull(knownRoles, "knownRoles"));
        Objects.requireNonNull(allowedTargets, "allowedTargets");

        // Validation walks the caller's own map, so an entry naming a role
        // outside knownRoles still throws - iterating Role.values() instead
        // would silently skip exactly the entries these two checks exist to
        // reject.
        for (Map.Entry<Role, Set<Role>> entry : allowedTargets.entrySet()) {
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
        }

        // Storage order, by contrast, is canonicalized on Role's own
        // declaration order rather than inherited from the caller. Since
        // Increment 25 the declared matrix is readable back (and reaches
        // serialized output via GovernanceJson), and every caller in this
        // project builds it with Map.of or Collectors.toMap - neither of
        // which specifies an iteration order, and Map.of deliberately
        // randomizes it per JVM invocation. Preserving the caller's order
        // would therefore make identical governance produce
        // differently-ordered JSON across runs, which section 14 forbids.
        // A role declared known but given no entry stays absent: inventing
        // an empty entry for it would be deriving a matrix rather than
        // reporting one.
        Map<Role, Set<Role>> copy = new LinkedHashMap<>();
        for (Role source : Role.values()) {
            Set<Role> targets = allowedTargets.get(source);
            if (targets != null) {
                copy.put(source, canonical(targets));
            }
        }
        this.allowedTargets = Collections.unmodifiableMap(copy);
    }

    /**
     * An immutable, {@code Role}-declaration-ordered copy. {@code
     * EnumSet.copyOf} throws {@code IllegalArgumentException} on an empty
     * collection when it cannot infer the element type, so the empty case
     * is built explicitly - a policy may legitimately know no roles, or
     * permit a known role no targets at all.
     */
    private static Set<Role> canonical(Collection<Role> roles) {
        return Collections.unmodifiableSet(
                roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles));
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

    /**
     * Every role this policy can judge, in {@link Role} declaration
     * order (Increment 25, OQ-02: a governance declaration nobody can
     * read back is not inspectable).
     *
     * <p>This does not weaken the no-derivation guarantee above. That
     * guarantee is about <em>construction</em> — no factory may infer a
     * matrix from an ordering. These accessors run the opposite
     * direction: they report what was explicitly declared, and cannot be
     * used to build a policy out of an ordering.
     */
    public Set<Role> knownRoles() {
        return knownRoles;
    }

    /**
     * The declared matrix, exactly as declared: source role to the roles
     * it may call, in {@link Role} declaration order at both levels. A
     * role that is {@linkplain #knowsRole known} but was given no entry
     * is <em>absent</em> here rather than mapped to an empty set — see
     * the constructor on why materializing one would be derivation.
     */
    public Map<Role, Set<Role>> allowedTargets() {
        return allowedTargets;
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
