package org.aerf.analysis.calibration;

import org.aerf.analysis.metrics.cycle.CycleEntropyCalculator;
import org.aerf.analysis.metrics.layer.LayerEntropyCalculator;
import org.aerf.analysis.metrics.layer.LayerPolicy;
import org.aerf.analysis.metrics.persistence.PersistenceEntropyCalculator;
import org.aerf.analysis.metrics.security.SecurityEntropyCalculator;
import org.aerf.analysis.metrics.security.rules.DefaultSecurityRules;
import org.aerf.model.Evidence;
import org.aerf.model.ExecutionContext;
import org.aerf.model.ExtractionFidelity;
import org.aerf.model.Graph;
import org.aerf.model.Node;
import org.aerf.model.NodeId;
import org.aerf.model.NodeRef;
import org.aerf.model.NodeType;
import org.aerf.model.RelationType;
import org.aerf.model.Role;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the full section-5 pipeline (aggregation, maturity,
 * confidence) against real output from all four MVP entropy calculators
 * on one graph, rather than against synthetic dimension values — proof
 * that {@code AggregatedEntropy} actually composes with the metrics
 * built in Increments 3, 5, 6, and 7, not just with hand-fed numbers.
 */
class CalibrationIntegrationTest {

    private static Node component(String id, Role role) {
        return Node.of(NodeId.of(id), NodeType.COMPONENT, role, Map.of(), List.of());
    }

    @Test
    void aggregatesAllFourMvpDimensionsAndClassifiesMaturity() {
        Node controller = component("web.OrderController", Role.PRESENTATION);
        Node service = component("service.OrderService", Role.APPLICATION);
        Node repository = component("repo.OrderRepository", Role.PERSISTENCE);
        Node view = Node.of(NodeId.of("order-detail.jsp"), NodeType.VIEW, Role.PRESENTATION, Map.of(),
                List.of(Evidence.of("jsp", "renders ${order.notes} as unescaped output", ExtractionFidelity.L1_SYNTAX)));

        Graph graph = Graph.builder()
                .addNode(controller)
                .addNode(service)
                .addNode(repository)
                .addNode(view)
                .addEdge(NodeRef.resolved(controller.id()), NodeRef.resolved(service.id()), RelationType.CALL, List.of())
                .addEdge(NodeRef.resolved(service.id()), NodeRef.resolved(repository.id()), RelationType.CALL,
                        List.of(Evidence.of("java", "iterated call", ExtractionFidelity.L2_SYMBOL_RESOLVED, ExecutionContext.ITERATED)))
                // deliberate layering violation, also not iterated (so persistence entropy isn't affected by it)
                .addEdge(NodeRef.resolved(controller.id()), NodeRef.resolved(repository.id()), RelationType.CALL,
                        List.of(Evidence.of("java", "controller calls repository directly", ExtractionFidelity.L2_SYMBOL_RESOLVED)))
                .build();

        LayerPolicy layerPolicy = LayerPolicy.of(
                Set.of(Role.PRESENTATION, Role.APPLICATION, Role.PERSISTENCE),
                Map.of(
                        Role.PRESENTATION, Set.of(Role.PRESENTATION, Role.APPLICATION),
                        Role.APPLICATION, Set.of(Role.APPLICATION, Role.PERSISTENCE),
                        Role.PERSISTENCE, Set.of(Role.PERSISTENCE)));

        OptionalDouble layerValue = LayerEntropyCalculator.withCallAndDependsRelations(layerPolicy).compute(graph).value();
        OptionalDouble cycleValue = CycleEntropyCalculator.withCallAndDependsRelations(false).compute(graph).value();
        OptionalDouble persistenceValue = PersistenceEntropyCalculator.withCallRelation().compute(graph).value();
        OptionalDouble securityValue = new SecurityEntropyCalculator(DefaultSecurityRules.illustrativeRules()).compute(graph).value();

        // Sanity-check the raw metric outputs before aggregating them, so a
        // failure here points at the metric, not at the aggregation math.
        assertEquals(OptionalDouble.of(1.0 / 3.0), layerValue, "one violation (controller->repository) out of three CALL edges");
        assertEquals(OptionalDouble.of(0.0), cycleValue, "no cycles among these nodes");
        assertEquals(OptionalDouble.of(0.5), persistenceValue, "one of two CALLs to the repository is iterated");
        assertEquals(OptionalDouble.of(1.0), securityValue, "the view's only rendering evidence is unescaped output");

        CalibrationProfile profile = CalibrationProfile.of(List.of(
                new WeightedDimension("layer", 0.25, new LinearCalibration()),
                new WeightedDimension("cycle", 0.25, new LinearCalibration()),
                new WeightedDimension("persistence", 0.25, new LinearCalibration()),
                new WeightedDimension("security", 0.25, new LinearCalibration())));

        Map<String, OptionalDouble> dimensionValues = Map.of(
                "layer", layerValue,
                "cycle", cycleValue,
                "persistence", persistenceValue,
                "security", securityValue);

        OptionalDouble totalEntropy = AggregatedEntropy.compute(profile, dimensionValues);
        double expectedTotal = 0.25 * (1.0 / 3.0) + 0.25 * 0.0 + 0.25 * 0.5 + 0.25 * 1.0;
        assertEquals(expectedTotal, totalEntropy.getAsDouble(), 1e-9);

        OptionalDouble maturity = Maturity.compute(totalEntropy);
        assertEquals(1.0 - expectedTotal, maturity.getAsDouble(), 1e-9);
        assertEquals(MaturityLevel.L1_REACTIVE, MaturityLevel.classify(maturity.getAsDouble()));

        // The graph has no unresolved edges, so confidence should be full
        // even though the aggregate entropy itself is unremarkable.
        assertEquals(OptionalDouble.of(1.0), AnalysisConfidence.compute(graph));
    }
}
