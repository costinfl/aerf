package org.aerf.analysis.metrics.security.rules;

import org.aerf.analysis.metrics.security.SecurityOpportunityRule;
import org.aerf.model.Evidence;
import org.aerf.model.ExtractionFidelity;
import org.aerf.model.Node;
import org.aerf.model.NodeId;
import org.aerf.model.NodeType;
import org.aerf.model.Role;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSecurityRulesTest {

    private final SecurityOpportunityRule rule = new DefaultSecurityRules.UnencodedViewOutputRule();

    private static Node viewNode(List<Evidence> evidence) {
        return Node.of(NodeId.of("order-detail.jsp"), NodeType.VIEW, Role.PRESENTATION, Map.of(), evidence);
    }

    @Test
    void unescapedOutputIsFlaggedAsAWeakness() {
        Node node = viewNode(List.of(Evidence.of("jsp", "renders ${order.notes} as unescaped output", ExtractionFidelity.L1_SYNTAX)));

        Optional<SecurityOpportunityRule.Finding> finding = rule.evaluate(node);

        assertTrue(finding.isPresent());
        assertEquals("xss", finding.get().concern());
        assertTrue(finding.get().weaknessDetected());
    }

    @Test
    void escapedOutputIsAnOpportunityButNotAWeakness() {
        Node node = viewNode(List.of(Evidence.of("jsp", "renders order.notes via c:out as escaped output", ExtractionFidelity.L1_SYNTAX)));

        Optional<SecurityOpportunityRule.Finding> finding = rule.evaluate(node);

        assertTrue(finding.isPresent());
        assertFalse(finding.get().weaknessDetected());
    }

    @Test
    void aViewNodeWithNoRenderingEvidenceIsNotAnOpportunityAtAll() {
        Node node = viewNode(List.of());

        assertTrue(rule.evaluate(node).isEmpty(), "no evidence means no claim either way, not a false negative");
    }

    @Test
    void aNonViewNodeIsNeverApplicable() {
        Node node = Node.of(NodeId.of("OrderController"), NodeType.COMPONENT, Role.PRESENTATION, Map.of(),
                List.of(Evidence.of("jsp", "renders x as unescaped output", ExtractionFidelity.L1_SYNTAX)));

        assertTrue(rule.evaluate(node).isEmpty(), "this rule only applies to VIEW nodes");
    }
}
