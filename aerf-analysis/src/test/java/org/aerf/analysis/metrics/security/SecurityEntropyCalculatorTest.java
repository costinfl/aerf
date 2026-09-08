package org.aerf.analysis.metrics.security;

import org.aerf.analysis.metrics.security.rules.DefaultSecurityRules;
import org.aerf.model.Evidence;
import org.aerf.model.ExtractionFidelity;
import org.aerf.model.Graph;
import org.aerf.model.Node;
import org.aerf.model.NodeId;
import org.aerf.model.NodeType;
import org.aerf.model.Role;
import org.aerf.model.fixtures.CanonicalSampleGraphs;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityEntropyCalculatorTest {

    private final SecurityEntropyCalculator calculator = new SecurityEntropyCalculator(DefaultSecurityRules.illustrativeRules());

    private static Node viewNode(String id, List<Evidence> evidence) {
        return Node.of(NodeId.of(id), NodeType.VIEW, Role.PRESENTATION, Map.of(), evidence);
    }

    @Test
    void oneFlaggedAmongThreeOpportunitiesGivesOneThird() {
        Graph graph = Graph.builder()
                .addNode(viewNode("a.jsp", List.of(Evidence.of("jsp", "renders as unescaped output", ExtractionFidelity.L1_SYNTAX))))
                .addNode(viewNode("b.jsp", List.of(Evidence.of("jsp", "renders as escaped output", ExtractionFidelity.L1_SYNTAX))))
                .addNode(viewNode("c.jsp", List.of(Evidence.of("jsp", "renders as escaped output", ExtractionFidelity.L1_SYNTAX))))
                .build();

        SecurityEntropyResult result = calculator.compute(graph);

        assertEquals(3, result.opportunities().size());
        assertEquals(1, result.flagged().size());
        assertEquals(OptionalDouble.of(1.0 / 3.0), result.value());
    }

    @Test
    void viewNodesWithNoRenderingEvidenceContributeNoOpportunities() {
        Graph graph = Graph.builder()
                .addNode(viewNode("blank.jsp", List.of()))
                .build();

        SecurityEntropyResult result = calculator.compute(graph);

        assertTrue(result.opportunities().isEmpty());
        assertTrue(result.value().isEmpty(), "no opportunities means undefined, not zero");
    }

    @Test
    void theFixtureGraphHasNoViewNodesSoNoOpportunitiesExist() {
        // A regression check: the Increment 1 fixture predates VIEW nodes
        // entirely, so this metric must not fabricate findings from
        // COMPONENT/DATA nodes it was never designed to look at.
        Graph graph = CanonicalSampleGraphs.layeredOrderSlice();

        SecurityEntropyResult result = calculator.compute(graph);

        assertTrue(result.opportunities().isEmpty());
        assertTrue(result.value().isEmpty());
    }

    @Test
    void findingsRetainTraceableRationale() {
        Graph graph = Graph.builder()
                .addNode(viewNode("a.jsp", List.of(Evidence.of("jsp", "renders ${x} as unescaped output", ExtractionFidelity.L1_SYNTAX))))
                .build();

        SecurityEntropyResult result = calculator.compute(graph);

        SecurityFinding finding = result.flagged().get(0);
        assertEquals(NodeId.of("a.jsp"), finding.nodeId());
        assertEquals("xss", finding.concern());
        assertTrue(finding.rationale().contains("unescaped output"));
    }

    @Test
    void resultIsDeterministicAcrossRepeatedRuns() {
        Graph graph = Graph.builder()
                .addNode(viewNode("a.jsp", List.of(Evidence.of("jsp", "renders as unescaped output", ExtractionFidelity.L1_SYNTAX))))
                .build();

        assertEquals(calculator.compute(graph), calculator.compute(graph));
    }
}
