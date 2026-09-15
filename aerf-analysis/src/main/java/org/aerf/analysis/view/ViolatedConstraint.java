package org.aerf.analysis.view;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * One §6.3 invariant as the governance view reports it (Increment 31,
 * OQ-16), answering the commission's third question — "what governance
 * constraints were violated" — at the granularity an organization
 * declared them.
 *
 * <p>Every invariant appears, held or violated. Listing only the
 * violations would leave a reader unable to tell a satisfied constraint
 * from one that was never configured, and §5.4's principle that
 * incomplete evidence stays visible applies to governance evidence too.
 * {@code indicatorValue} is §6.1's own {@code I_k(S)}, so it reads 0 for
 * a constraint that held.
 *
 * <p>{@code weight} is λ_k as declared, undefined when the organization
 * declared no weights at all. It sits beside the indicator rather than
 * multiplied into anything here: the product is {@code
 * InvariantContribution}'s, and this record does not recompute it.
 *
 * @param invariantName  the declared name, the join key everything in this project uses
 * @param severity       the invariant's own free-text severity. Carried, never interpreted:
 *                       Increment 29 refused to map it to a number and this increment
 *                       refuses to map it to a verdict, for the same reason — {@code
 *                       Invariant}'s javadoc keeps it open so no taxonomy is asserted
 * @param indicatorValue §6.1's I_k(S): 0 when the invariant held, 1 when it was violated
 * @param weight         λ_k as declared, undefined when no weights were declared
 * @param violations     each violation with its own subject and excusal status;
 *                       empty when the invariant held
 */
public record ViolatedConstraint(
        String invariantName,
        String severity,
        int indicatorValue,
        OptionalDouble weight,
        List<TraceableFinding> violations) {

    public ViolatedConstraint {
        Objects.requireNonNull(invariantName, "invariantName");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(weight, "weight");
        violations = List.copyOf(Objects.requireNonNull(violations, "violations"));
        if (invariantName.isBlank()) {
            throw new IllegalArgumentException("invariantName must not be blank");
        }
        if (indicatorValue != 0 && indicatorValue != 1) {
            throw new IllegalArgumentException(
                    "I_k(S) is an indicator and is 0 or 1; got " + indicatorValue);
        }
        if ((indicatorValue == 1) != !violations.isEmpty()) {
            throw new IllegalArgumentException(
                    "indicator " + indicatorValue + " contradicts " + violations.size()
                            + " violation(s) for '" + invariantName + "'");
        }
    }

    /** Whether this constraint held. The inverse of §6.1's indicator. */
    public boolean holds() {
        return indicatorValue == 0;
    }

    /**
     * Violations the organization has <em>not</em> already accepted.
     *
     * <p>This is a reading of the list, never a filter applied to it: the
     * excused ones remain in {@link #violations()} and in
     * {@link #indicatorValue()}. An exception accepts a finding rather
     * than denying it (Increment 28), so excusing every violation of an
     * invariant does not make that invariant hold.
     */
    public List<TraceableFinding> unexcusedViolations() {
        return violations.stream().filter(finding -> !finding.isExcused()).toList();
    }
}
