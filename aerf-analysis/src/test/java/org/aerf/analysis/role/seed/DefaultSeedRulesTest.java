package org.aerf.analysis.role.seed;

import org.aerf.analysis.role.RoleInferenceRule;
import org.aerf.model.Evidence;
import org.aerf.model.ExtractionFidelity;
import org.aerf.model.Node;
import org.aerf.model.NodeId;
import org.aerf.model.NodeType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSeedRulesTest {

    @Test
    void illustrativeRulesAreUniquelyNamed() {
        List<RoleInferenceRule> rules = DefaultSeedRules.illustrativeRules();

        assertEquals(5, rules.size());
        assertEquals(5, rules.stream().map(RoleInferenceRule::name).distinct().count());
    }

    @Test
    void persistenceRuleFiresOnlyForSpringDataAdapterEvidence() {
        RoleInferenceRule rule = new DefaultSeedRules.PersistenceBySpringDataAdapter();

        Node withEvidence = Node.withUnknownRole(NodeId.of("a"), NodeType.COMPONENT, Map.of(),
                List.of(Evidence.of("spring-data", "Repository interface", ExtractionFidelity.L2_SYMBOL_RESOLVED)));
        Node without = Node.withUnknownRole(NodeId.of("b"), NodeType.COMPONENT, Map.of(), List.of());

        assertTrue(rule.evaluate(withEvidence).isPresent());
        assertTrue(rule.evaluate(without).isEmpty());
    }

    @Test
    void controllerRuleRequiresBothTheSpringAdapterAndTheAnnotationText() {
        RoleInferenceRule rule = new DefaultSeedRules.PresentationBySpringControllerAnnotation();

        Node matching = Node.withUnknownRole(NodeId.of("a"), NodeType.COMPONENT, Map.of(),
                List.of(Evidence.of("spring", "@Controller annotation", ExtractionFidelity.L2_SYMBOL_RESOLVED)));
        Node wrongAdapter = Node.withUnknownRole(NodeId.of("b"), NodeType.COMPONENT, Map.of(),
                List.of(Evidence.of("java", "@Controller annotation", ExtractionFidelity.L1_SYNTAX)));
        Node wrongAnnotation = Node.withUnknownRole(NodeId.of("c"), NodeType.COMPONENT, Map.of(),
                List.of(Evidence.of("spring", "@Service annotation", ExtractionFidelity.L2_SYMBOL_RESOLVED)));

        assertTrue(rule.evaluate(matching).isPresent());
        assertTrue(rule.evaluate(wrongAdapter).isEmpty());
        assertTrue(rule.evaluate(wrongAnnotation).isEmpty());
    }

    @Test
    void controllerRuleFiresFromTheStructuredAnnotationAttributeAlone() {
        // No "@Controller" substring anywhere in the description: only the
        // structured attribute (AERF v0.4.1 patch Amendment 5) makes this match.
        RoleInferenceRule rule = new DefaultSeedRules.PresentationBySpringControllerAnnotation();
        Node node = Node.withUnknownRole(NodeId.of("a"), NodeType.COMPONENT, Map.of(),
                List.of(Evidence.builder("spring", "web-tier stereotype annotation observed", ExtractionFidelity.L2_SYMBOL_RESOLVED)
                        .attribute("annotation", "org.springframework.stereotype.Controller")
                        .build()));

        assertTrue(rule.evaluate(node).isPresent());
    }

    @Test
    void serviceRuleFiresFromTheStructuredAnnotationAttributeAlone() {
        RoleInferenceRule rule = new DefaultSeedRules.ApplicationBySpringServiceAnnotation();
        Node node = Node.withUnknownRole(NodeId.of("a"), NodeType.COMPONENT, Map.of(),
                List.of(Evidence.builder("spring", "service-tier stereotype annotation observed", ExtractionFidelity.L2_SYMBOL_RESOLVED)
                        .attribute("annotation", "org.springframework.stereotype.Service")
                        .build()));

        assertTrue(rule.evaluate(node).isPresent());
    }

    @Test
    void domainRuleFiresOnlyForDataNodeType() {
        RoleInferenceRule rule = new DefaultSeedRules.DomainByDataNodeType();

        assertTrue(rule.evaluate(Node.withUnknownRole(NodeId.of("a"), NodeType.DATA, Map.of(), List.of())).isPresent());
        assertTrue(rule.evaluate(Node.withUnknownRole(NodeId.of("b"), NodeType.COMPONENT, Map.of(), List.of())).isEmpty());
    }

    @Test
    void infrastructureRuleFiresOnlyForConfigNodeType() {
        RoleInferenceRule rule = new DefaultSeedRules.InfrastructureByConfigNodeType();

        assertTrue(rule.evaluate(Node.withUnknownRole(NodeId.of("a"), NodeType.CONFIG, Map.of(), List.of())).isPresent());
        assertTrue(rule.evaluate(Node.withUnknownRole(NodeId.of("b"), NodeType.COMPONENT, Map.of(), List.of())).isEmpty());
    }
}
