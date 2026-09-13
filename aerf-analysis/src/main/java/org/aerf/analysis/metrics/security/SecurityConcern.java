package org.aerf.analysis.metrics.security;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Stable, machine-referenceable identifiers for the security concerns a
 * {@link SecurityOpportunityRule} can report — the post-v0.4.1 backlog's
 * OQ-11, resolved in increment 22's successor (see
 * {@code docs/increment-23-security-concern-identifiers.md}).
 *
 * <p><b>Deliberately constants, not an enum.</b> AERF v0.4 §4.4 names XSS,
 * CSRF and mass-assignment as <em>examples</em> of governance-relevant
 * weaknesses, not a closed set — unlike {@link org.aerf.model.Role} or
 * {@link org.aerf.model.RelationType}, which the specification does define
 * exhaustively. An enum here would assert a closure the specification
 * declines to make, and would prevent an organization from adding a
 * concern of its own without modifying this project. A named constant
 * gives the stability the original question asked for ("so a consumer can
 * reference a concern reliably") while leaving the set open.
 *
 * <p><b>What this does not do.</b> OQ-11's stated motivation was that
 * invariants could reference concerns. They still cannot: the invariant
 * DSL resolves exactly eight {@code PropertyKey} constants, none of them
 * security-related, and {@code InvariantEvaluator} never receives a
 * {@link SecurityEntropyResult} at all. Only the aggregate security ratio
 * is reachable, as a GRAPH-scope metric. Making a finding's concern
 * addressable from the DSL requires deciding how a finding is scoped and
 * evaluated — a security-model design question this class deliberately
 * does not answer. That half of OQ-11 stays open and is recorded as such
 * in {@code docs/post-v0.4.1-backlog-status.md}.
 */
public final class SecurityConcern {

    /**
     * Cross-site scripting: unencoded output reaching a rendered view.
     * The only concern any rule in this project currently reports.
     */
    public static final String XSS = "xss";

    /**
     * The canonical form: lowercase ASCII words joined by single hyphens.
     * Chosen so a concern is safe to use as a map key, a JSON value and a
     * future DSL literal without per-consumer normalization, and so
     * {@code "XSS"}, {@code "Cross Site Scripting"} and {@code "xss "}
     * cannot all denote the same thing.
     */
    private static final Pattern CANONICAL = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    private SecurityConcern() {
    }

    /**
     * @return {@code concern} unchanged, if it is in canonical form
     * @throws NullPointerException if {@code concern} is null
     * @throws IllegalArgumentException if {@code concern} is blank or not
     *     canonical. Deliberately rejects rather than normalizes: silently
     *     lowercasing {@code "Mass Assignment"} into {@code "mass-assignment"}
     *     would invent an identifier the rule author never chose.
     */
    public static String requireCanonical(String concern) {
        Objects.requireNonNull(concern, "concern");
        if (concern.isBlank()) {
            throw new IllegalArgumentException("concern must not be blank");
        }
        if (!CANONICAL.matcher(concern).matches()) {
            throw new IllegalArgumentException(
                    "concern must be lowercase alphanumeric words separated by single hyphens "
                            + "(e.g. \"" + XSS + "\", \"mass-assignment\"); got \"" + concern + "\"");
        }
        return concern;
    }
}
