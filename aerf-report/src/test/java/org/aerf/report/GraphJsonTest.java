package org.aerf.report;

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
import org.aerf.model.fixtures.CanonicalSampleGraphs;
import org.aerf.report.json.JsonValue;
import org.aerf.report.json.JsonWriter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphJsonTest {

    @Test
    void nodeSerializesTypeRoleAttributesAndEvidence() {
        Node node = Node.of(NodeId.of("web.OrderController"), NodeType.COMPONENT, Role.PRESENTATION,
                Map.of("framework", "spring-mvc"),
                List.of(Evidence.of("spring", "@Controller annotation", "OrderController.java:12", ExtractionFidelity.L2_SYMBOL_RESOLVED)));

        String json = JsonWriter.write(GraphJson.node(node));

        assertTrue(json.contains("\"id\":\"web.OrderController\""));
        assertTrue(json.contains("\"type\":\"COMPONENT\""));
        assertTrue(json.contains("\"role\":\"PRESENTATION\""));
        assertTrue(json.contains("\"framework\":\"spring-mvc\""));
        assertTrue(json.contains("\"sourceAdapter\":\"spring\""));
        assertTrue(json.contains("\"fidelity\":\"L2_SYMBOL_RESOLVED\""));
        assertTrue(json.contains("\"executionContext\":\"UNKNOWN\""), "default execution context must appear explicitly, not be omitted");
    }

    @Test
    void resolvedNodeRefSerializesWithItsId() {
        String json = JsonWriter.write(GraphJson.nodeRef(NodeRef.resolved(NodeId.of("a"))));

        assertTrue(json.contains("\"resolved\":true"));
        assertTrue(json.contains("\"id\":\"a\""));
    }

    @Test
    void unresolvedNodeRefSerializesWithItsDescriptionNotAFabricatedId() {
        String json = JsonWriter.write(GraphJson.nodeRef(NodeRef.unresolved("dynamic dispatch target")));

        assertTrue(json.contains("\"resolved\":false"));
        assertTrue(json.contains("\"description\":\"dynamic dispatch target\""));
        assertFalse(json.contains("\"id\""), "an unresolved ref must never carry a fabricated id field");
    }

    @Test
    void evidenceWithNoLocationSerializesLocationAsNull() {
        Evidence evidence = Evidence.of("java", "class declaration observed", ExtractionFidelity.L1_SYNTAX);

        String json = JsonWriter.write(GraphJson.evidence(evidence));

        assertTrue(json.contains("\"location\":null"));
    }

    @Test
    void iteratedExecutionContextIsVisibleInTheEdgesProvenance() {
        Node service = Node.of(NodeId.of("service"), NodeType.COMPONENT, Role.APPLICATION, Map.of(), List.of());
        Node repository = Node.of(NodeId.of("repository"), NodeType.COMPONENT, Role.PERSISTENCE, Map.of(), List.of());
        Graph graph = Graph.builder()
                .addNode(service)
                .addNode(repository)
                .addEdge(NodeRef.resolved(service.id()), NodeRef.resolved(repository.id()), RelationType.CALL,
                        List.of(Evidence.of("java", "call inside loop", ExtractionFidelity.L2_SYMBOL_RESOLVED, ExecutionContext.ITERATED)))
                .build();

        String json = JsonWriter.write(GraphJson.graph(graph));

        assertTrue(json.contains("\"executionContext\":\"ITERATED\""));
    }

    @Test
    void theWholeFixtureGraphSerializesWithoutError() {
        Graph graph = CanonicalSampleGraphs.layeredOrderSlice();

        JsonValue json = GraphJson.graph(graph);
        String written = JsonWriter.write(json);

        assertTrue(written.contains("\"nodes\":["));
        assertTrue(written.contains("\"edges\":["));
        // The fixture's unresolved COMMUNICATES edge to an external system.
        assertTrue(written.contains("external JDBC datasource"));
    }
}
