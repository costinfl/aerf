package org.aerf.analysis.metrics.security;

import org.aerf.model.Node;

import java.util.Objects;
import java.util.Optional;

/**
 * One rule contributing to AERF v0.4 section 4.4's security entropy: does
 * this node represent an applicable control opportunity for some security
 * concern, and if so, was a weakness in that control actually detected?
 *
 * <p>Section 4.4 gives XSS exposure, missing/ineffective CSRF protection,
 * and mass-assignment/data-binding exposure only as examples ("covers
 * governance-relevant weaknesses such as...") rather than a closed set —
 * unlike {@link org.aerf.model.Role} or {@link org.aerf.model.RelationType},
 * which v0.4 defines with closed-set notation. So a rule names its own
 * concern as a free-text label, not a value from some fixed enum this
 * implementation would otherwise be asserting as canonical.
 *
 * <p>Section 4.4: "The metric represents detected control weaknesses,
 * not mere technology presence." A rule must therefore never treat mere
 * absence of evidence as a weakness — {@link #evaluate(Node)} returns
 * empty when there is no evidence to assess at all, not a false
 * "opportunity, not weak" or "opportunity, weak" result.
 */
public interface SecurityOpportunityRule {

    String name();

    Optional<Finding> evaluate(Node node);

    record Finding(String concern, boolean weaknessDetected, String rationale) {
        public Finding {
            Objects.requireNonNull(concern, "concern");
            Objects.requireNonNull(rationale, "rationale");
            if (concern.isBlank()) {
                throw new IllegalArgumentException("concern must not be blank");
            }
            if (rationale.isBlank()) {
                throw new IllegalArgumentException("rationale must not be blank");
            }
        }
    }
}
