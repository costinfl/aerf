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
        Objects.requireNonNull(concern, "concern");
        Objects.requireNonNull(rationale, "rationale");
    }
}
