package org.aerf.analysis.invariant;

import java.util.Objects;

/** One specific instance (a node, an edge, or the graph itself) that failed an invariant's assertion. */
public record InvariantViolation(ViolationSubject subject, String rationale) {
    public InvariantViolation {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(rationale, "rationale");
    }
}
