package org.aerf.analysis.invariant;

import java.util.Objects;

/**
 * One governance invariant (AERF v0.4 section 6), corresponding directly
 * to the conceptual rule syntax in section 6.3:
 *
 * <pre>
 * invariant name {
 *   scope: ...
 *   when: ...
 *   assert: ...
 *   severity: ...
 * }
 * </pre>
 *
 * <p>{@code severity} is a free-text label, not a closed enum — AERF
 * v0.4 never enumerates a fixed severity vocabulary; both worked
 * examples in the specification (sections 6.3 and 6.4) use only
 * {@code "critical"}. This follows the same reasoning as
 * {@code SecurityOpportunityRule.Finding}'s {@code concern} field
 * (Increment 7): inventing a closed set here would assert a taxonomy the
 * specification does not.
 */
public record Invariant(String name, Scope scope, Predicate when, Predicate assertion, String severity) {

    public Invariant {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(when, "when");
        Objects.requireNonNull(assertion, "assertion");
        Objects.requireNonNull(severity, "severity");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (severity.isBlank()) {
            throw new IllegalArgumentException("severity must not be blank");
        }
    }
}
