package org.aerf.report;

import org.aerf.analysis.metrics.cycle.CycleEntropyCalculator;
import org.aerf.analysis.metrics.cycle.CycleEntropyResult;
import org.aerf.analysis.metrics.cycle.SubsystemCycleEntropy;
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
import org.aerf.report.json.JsonWriter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsJsonTest {

    @Test
    void layerEntropySerializesValueAndViolatingEdges() {
        Node controller = Node.of(NodeId.of("controller"), NodeType.COMPONENT, Role.PRESENTATION, Map.of(), List.of());
        Node repository = Node.of(NodeId.of("repository"), NodeType.COMPONENT, Role.PERSISTENCE, Map.of(), List.of());
        Graph graph = Graph.builder()
                .addNode(controller)
                .addNode(repository)
                .addEdge(NodeRef.resolved(controller.id()), NodeRef.resolved(repository.id()), RelationType.CALL, List.of())
                .build();
        LayerPolicy policy = LayerPolicy.of(Set.of(Role.PRESENTATION, Role.PERSISTENCE), Map.of());

        String json = JsonWriter.write(MetricsJson.layerEntropy(
                LayerEntropyCalculator.withCallAndDependsRelations(policy).compute(graph)));

        assertTrue(json.contains("\"value\":1.0"));
        assertTrue(json.contains("\"violatingEdgeCount\":1"));
    }

    @Test
    void cycleEntropySerializesUndefinedAsNullForAnEmptyGraph() {
        Graph graph = Graph.builder().build();

        String json = JsonWriter.write(MetricsJson.cycleEntropy(
                CycleEntropyCalculator.withCallAndDependsRelations(false).compute(graph)));

        assertTrue(json.contains("\"value\":null"));
        assertTrue(json.contains("\"totalNodeCount\":0"));
    }

    @Test
    void persistenceEntropySerializesBothValueAndWeightedValue() {
        Node service = Node.of(NodeId.of("service"), NodeType.COMPONENT, Role.APPLICATION, Map.of(), List.of());
        Node repository = Node.of(NodeId.of("repository"), NodeType.COMPONENT, Role.PERSISTENCE, Map.of(), List.of());
        Graph graph = Graph.builder()
                .addNode(service)
                .addNode(repository)
                .addEdge(NodeRef.resolved(service.id()), NodeRef.resolved(repository.id()), RelationType.CALL,
                        List.of(Evidence.of("java", "iterated call", ExtractionFidelity.L2_SYMBOL_RESOLVED, ExecutionContext.ITERATED)))
                .build();

        String json = JsonWriter.write(MetricsJson.persistenceEntropy(
                PersistenceEntropyCalculator.withCallRelation().compute(graph)));

        assertTrue(json.contains("\"value\":1.0"));
        assertTrue(json.contains("\"weightedValue\":1.0"));
    }

    @Test
    void securityEntropySerializesFindingsWithConcernAndRationale() {
        Node view = Node.of(NodeId.of("order.jsp"), NodeType.VIEW, Role.PRESENTATION, Map.of(),
                List.of(Evidence.of("jsp", "renders as unescaped output", ExtractionFidelity.L1_SYNTAX)));
        Graph graph = Graph.builder().addNode(view).build();

        String json = JsonWriter.write(MetricsJson.securityEntropy(
                new SecurityEntropyCalculator(DefaultSecurityRules.illustrativeRules()).compute(graph)));

        assertTrue(json.contains("\"concern\":\"xss\""));
        assertTrue(json.contains("\"weaknessDetected\":true"));
    }

    @Test
    void declaringNoSubsystemsEmitsAnEmptyBySubsystemArray() {
        String json = JsonWriter.write(MetricsJson.cycleEntropy(
                new CycleEntropyResult(3, List.of(Set.of(NodeId.of("a"), NodeId.of("b"))))));

        assertTrue(json.contains("\"bySubsystem\":[]"), json);
        assertTrue(json.contains("\"value\":0.6666666666666666"),
                "the graph-wide value is untouched by scoping: " + json);
    }

    @Test
    void anUnmeasurableSubsystemSerializesAsNullNotZero() {
        // OQ-06: a subsystem claiming no node in this graph has no reading
        // at all, which a reader must be able to tell apart from a
        // measured 0.0 meaning it has nodes and none are in a cycle.
        String json = JsonWriter.write(MetricsJson.cycleEntropy(new CycleEntropyResult(
                2, List.of(), List.of(
                        new SubsystemCycleEntropy("measured-acyclic", 2, Set.of()),
                        new SubsystemCycleEntropy("claims-nothing", 0, Set.of())))));

        assertTrue(json.contains("\"subsystem\":\"measured-acyclic\",\"value\":0.0"), json);
        assertTrue(json.contains("\"subsystem\":\"claims-nothing\",\"value\":null"), json);
    }

    @Test
    void scopedReadingsSerializeInDeclarationOrderWithTheirParticipatingNodes() {
        String json = JsonWriter.write(MetricsJson.cycleEntropy(new CycleEntropyResult(
                4, List.of(Set.of(NodeId.of("com.example.billing.Invoice"))), List.of(
                        new SubsystemCycleEntropy("billing", 2, Set.of(NodeId.of("com.example.billing.Invoice"))),
                        new SubsystemCycleEntropy("shipping", 2, Set.of())))));

        assertTrue(json.contains(
                // counts serialize as doubles: this writer has one number
                // type, as the committed sample reports already show.
                "\"bySubsystem\":[{\"subsystem\":\"billing\",\"value\":0.5,\"totalNodeCount\":2.0,"
                        + "\"participatingNodeCount\":1.0,"
                        + "\"participatingNodes\":[\"com.example.billing.Invoice\"]},"
                        + "{\"subsystem\":\"shipping\",\"value\":0.0,\"totalNodeCount\":2.0,"
                        + "\"participatingNodeCount\":0.0,\"participatingNodes\":[]}]"), json);
    }
}
