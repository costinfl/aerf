package org.aerf.analysis.metrics.security;

import org.aerf.model.NodeId;

import java.util.Objects;

/**
 * One resolved {@link SecurityOpportunityRule.Finding}, attributed to the
 * node and rule that produced it — kept in full on
 * {@link SecurityEntropyResult} so a security finding stays traceable to
 * its source, not just a count.
 */
public record SecurityFinding(NodeId nodeId, String ruleName, String concern, boolean weaknessDetected, String rationale) {
    public SecurityFinding {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(ruleName, "ruleName");
        // Increment 23: this record previously accepted any non-null
        // concern, including blank - a gap, since the Finding it is built
        // from has always rejected blanks. Both now enforce the same
        // canonical form, so a concern cannot enter a report in one shape
        // via one path and another shape via the other.
        concern = SecurityConcern.requireCanonical(concern);
        Objects.requireNonNull(rationale, "rationale");
    }
}
