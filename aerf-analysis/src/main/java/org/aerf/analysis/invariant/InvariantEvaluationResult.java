package org.aerf.analysis.invariant;

import java.util.List;
import java.util.Objects;

/**
 * The result of evaluating one {@link Invariant} against a graph.
 * {@code violations} keeps every specific instance that failed, not just
 * a count — {@link #indicatorValue()} collapses that down to section
 * 6.1's formal {@code I_k(S)} (0 if the invariant holds, 1 if violated)
 * only when that single bit is what's wanted; the full evidence stays
 * available alongside it.
 */
public record InvariantEvaluationResult(String invariantName, String severity, List<InvariantViolation> violations) {

    public InvariantEvaluationResult {
        Objects.requireNonNull(invariantName, "invariantName");
        Objects.requireNonNull(severity, "severity");
        violations = List.copyOf(Objects.requireNonNull(violations, "violations"));
    }

    public boolean holds() {
        return violations.isEmpty();
    }

    /** {@code I_k(S)} per AERF v0.4 section 6.1: 0 when the invariant holds, 1 when violated. */
    public int indicatorValue() {
        return violations.isEmpty() ? 0 : 1;
    }
}
