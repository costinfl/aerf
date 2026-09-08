package org.aerf.analysis.metrics.security.rules;

import org.aerf.analysis.metrics.security.SecurityOpportunityRule;
import org.aerf.model.Evidence;
import org.aerf.model.Node;
import org.aerf.model.NodeType;

import java.util.List;
import java.util.Optional;

/**
 * A small, illustrative catalog of security-opportunity rules.
 *
 * <p>Like {@code DefaultSeedRules} and {@code DefaultGraphRefinementRules},
 * this is an implementation decision, not part of AERF v0.4: the
 * specification names XSS, CSRF, and mass-assignment only as examples of
 * governance-relevant weaknesses (section 4.4), not a concrete detection
 * rule. Only XSS (unencoded view output) has a rule here. CSRF and
 * mass-assignment are deliberately not attempted in this increment — see
 * the increment notes for why (both are naturally edge/relationship
 * concerns, not single-node ones, and this increment scopes rules to one
 * node in isolation).
 */
public final class DefaultSecurityRules {

    private DefaultSecurityRules() {
    }

    public static List<SecurityOpportunityRule> illustrativeRules() {
        return List.of(new UnencodedViewOutputRule());
    }

    /**
     * Applicable only to {@link NodeType#VIEW} nodes carrying rendering
     * evidence. The convention (illustrative, not adapter-implemented
     * yet): a structured {@code outputEncoding} attribute (AERF v0.4.1
     * patch Amendment 5) valued {@code "unescaped"} or {@code "escaped"}
     * is preferred; when absent, evidence whose description contains
     * {@code "unescaped output"} is a detected weakness and
     * {@code "escaped output"} is an opportunity handled correctly. A
     * VIEW node with no such evidence at all is not an opportunity —
     * absence of evidence must not be read as absence of a control
     * weakness (section 4.4's "not mere technology presence").
     */
    static final class UnencodedViewOutputRule implements SecurityOpportunityRule {

        private static final String OUTPUT_ENCODING_ATTRIBUTE = "outputEncoding";
        private static final String UNESCAPED_MARKER = "unescaped output";
        private static final String ESCAPED_MARKER = "escaped output";

        @Override
        public String name() {
            return "xss-unencoded-view-output";
        }

        @Override
        public Optional<Finding> evaluate(Node node) {
            if (node.type() != NodeType.VIEW) {
                return Optional.empty();
            }
            for (Evidence evidence : node.evidence()) {
                String encoding = evidence.attributes().get(OUTPUT_ENCODING_ATTRIBUTE);
                String description = evidence.description();
                if ("unescaped".equals(encoding) || description.contains(UNESCAPED_MARKER)) {
                    return Optional.of(new Finding("xss", true, "unescaped output observed: " + description));
                }
                if ("escaped".equals(encoding) || description.contains(ESCAPED_MARKER)) {
                    return Optional.of(new Finding("xss", false, "output encoding observed: " + description));
                }
            }
            return Optional.empty();
        }
    }
}
