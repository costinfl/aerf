package org.aerf.extraction;

import org.aerf.model.Edge;
import org.aerf.model.Evidence;
import org.aerf.model.ExtractionFidelity;
import org.aerf.model.Graph;
import org.aerf.model.Node;
import org.aerf.model.NodeId;
import org.aerf.model.NodeRef;
import org.aerf.model.NodeType;
import org.aerf.model.RelationType;
import org.aerf.model.fixtures.CanonicalSampleGraphs;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphAssemblerTest {

    private final GraphAssembler assembler = new GraphAssembler();

    private static NodeFact node(String id, NodeType type) {
        return new NodeFact(NodeId.of(id), type, Map.of(), List.of());
    }

    private static NodeFact node(String id, NodeType type, Map<String, String> attributes) {
        return new NodeFact(NodeId.of(id), type, attributes, List.of());
    }

    private static Evidence evidence(String description) {
        return Evidence.of("java", description, ExtractionFidelity.L1_SYNTAX);
    }

    /** id, type, attributes, evidence - deliberately not role, which GraphAssembler never assigns. */
    private static List<String> nodeSnapshots(Graph graph) {
        return graph.nodes().stream()
                .map(n -> n.id().value() + "|" + n.type() + "|" + n.attributes() + "|" + n.evidence())
                .toList();
    }

    @Test
    void resolvesAReferenceToADeclaredNode() {
        Graph graph = assembler.assemble(
                List.of(node("a", NodeType.COMPONENT), node("b", NodeType.COMPONENT)),
                List.of(new EdgeFact(SymbolRef.of("a"), SymbolRef.of("b"), RelationType.CALL, List.of())));

        Edge edge = graph.edges().get(0);
        assertEquals(NodeRef.resolved(NodeId.of("a")), edge.source());
        assertEquals(NodeRef.resolved(NodeId.of("b")), edge.target());
    }

    @Test
    void anUndeclaredTargetResolvesToUnresolvedWithItsDescription() {
        Graph graph = assembler.assemble(
                List.of(node("a", NodeType.COMPONENT)),
                List.of(new EdgeFact(SymbolRef.of("a"), SymbolRef.of("com.example.Unknown", "unresolved call target"),
                        RelationType.CALL, List.of())));

        Edge edge = graph.edges().get(0);
        assertEquals(new NodeRef.Unresolved("unresolved call target"), edge.target());
    }

    @Test
    void anUnresolvedSourceEdgeIsRetainedNotDropped() {
        Graph graph = assembler.assemble(
                List.of(node("b", NodeType.COMPONENT)),
                List.of(new EdgeFact(SymbolRef.of("com.example.Unknown", "unresolved caller"), SymbolRef.of("b"),
                        RelationType.CALL, List.of())));

        assertEquals(1, graph.edges().size(), "an edge with an unresolved source is still evidence, not discarded");
        Edge edge = graph.edges().get(0);
        assertEquals(new NodeRef.Unresolved("unresolved caller"), edge.source());
        assertEquals(NodeRef.resolved(NodeId.of("b")), edge.target());
    }

    @Test
    void duplicateFactsWithNonConflictingAttributesAreMerged() {
        NodeFact first = node("a", NodeType.COMPONENT, Map.of("framework", "spring"));
        NodeFact second = new NodeFact(NodeId.of("a"), NodeType.COMPONENT, Map.of("layer", "web"),
                List.of(evidence("second observation")));

        Graph graph = assembler.assemble(List.of(first, second), List.of());

        Node merged = graph.node(NodeId.of("a")).orElseThrow();
        assertEquals(Map.of("framework", "spring", "layer", "web"), merged.attributes());
        assertEquals(1, merged.evidence().size());
    }

    @Test
    void duplicateFactsWithTheSameAttributeAndSameValueDoNotConflict() {
        NodeFact first = node("a", NodeType.COMPONENT, Map.of("framework", "spring"));
        NodeFact second = node("a", NodeType.COMPONENT, Map.of("framework", "spring"));

        Graph graph = assembler.assemble(List.of(first, second), List.of());

        assertEquals(Map.of("framework", "spring"), graph.node(NodeId.of("a")).orElseThrow().attributes());
    }

    @Test
    void duplicateFactsWithConflictingTypesThrow() {
        NodeFact asComponent = node("a", NodeType.COMPONENT);
        NodeFact asData = node("a", NodeType.DATA);

        assertThrows(IllegalStateException.class, () -> assembler.assemble(List.of(asComponent, asData), List.of()));
    }

    @Test
    void duplicateFactsWithConflictingAttributeValuesThrow() {
        NodeFact first = node("a", NodeType.COMPONENT, Map.of("framework", "spring"));
        NodeFact second = node("a", NodeType.COMPONENT, Map.of("framework", "hibernate"));

        assertThrows(IllegalStateException.class, () -> assembler.assemble(List.of(first, second), List.of()));
    }

    @Test
    void resultIsIndependentOfInputFactOrder() {
        List<NodeFact> nodeFacts = new ArrayList<>(List.of(
                node("zeta", NodeType.COMPONENT, Map.of("k", "1")),
                node("alpha", NodeType.COMPONENT),
                new NodeFact(NodeId.of("alpha"), NodeType.COMPONENT, Map.of("k", "2"), List.of(evidence("extra"))),
                node("mid", NodeType.DATA)));
        List<EdgeFact> edgeFacts = new ArrayList<>(List.of(
                new EdgeFact(SymbolRef.of("zeta"), SymbolRef.of("alpha"), RelationType.CALL, List.of(evidence("call 1"))),
                new EdgeFact(SymbolRef.of("alpha"), SymbolRef.of("mid"), RelationType.DEPENDS, List.of(evidence("call 2")))));

        Graph first = assembler.assemble(nodeFacts, edgeFacts);

        Collections.shuffle(nodeFacts, new java.util.Random(42));
        Collections.shuffle(edgeFacts, new java.util.Random(7));
        Graph second = assembler.assemble(nodeFacts, edgeFacts);

        assertEquals(nodeSnapshots(first), nodeSnapshots(second));
        assertEquals(first.edges(), second.edges());
    }

    @Test
    void reproducesTheCanonicalSampleGraphsShapeFromFacts() {
        List<NodeFact> nodeFacts = List.of(
                new NodeFact(CanonicalSampleGraphs.ORDER_CONTROLLER, NodeType.COMPONENT, Map.of("framework", "spring-mvc"),
                        List.of(Evidence.of("spring", "@Controller annotation", ExtractionFidelity.L2_SYMBOL_RESOLVED))),
                new NodeFact(CanonicalSampleGraphs.ORDER_SERVICE, NodeType.COMPONENT, Map.of("framework", "spring"),
                        List.of(Evidence.of("spring", "@Service annotation", ExtractionFidelity.L2_SYMBOL_RESOLVED))),
                new NodeFact(CanonicalSampleGraphs.ORDER, NodeType.DATA, Map.of(),
                        List.of(Evidence.of("java", "plain class, no persistence annotations", ExtractionFidelity.L1_SYNTAX))),
                new NodeFact(CanonicalSampleGraphs.ORDER_REPOSITORY, NodeType.COMPONENT, Map.of("framework", "spring-data"),
                        List.of(Evidence.of("spring-data", "Repository interface", ExtractionFidelity.L2_SYMBOL_RESOLVED))));

        List<EdgeFact> edgeFacts = List.of(
                new EdgeFact(SymbolRef.of(CanonicalSampleGraphs.ORDER_CONTROLLER.value()), SymbolRef.of(CanonicalSampleGraphs.ORDER_SERVICE.value()),
                        RelationType.CALL, List.of(Evidence.of("java", "controller method calls service method", ExtractionFidelity.L2_SYMBOL_RESOLVED))),
                new EdgeFact(SymbolRef.of(CanonicalSampleGraphs.ORDER_SERVICE.value()), SymbolRef.of(CanonicalSampleGraphs.ORDER.value()),
                        RelationType.DEPENDS, List.of(Evidence.of("java", "service method parameter/return type", ExtractionFidelity.L2_SYMBOL_RESOLVED))),
                new EdgeFact(SymbolRef.of(CanonicalSampleGraphs.ORDER_SERVICE.value()), SymbolRef.of(CanonicalSampleGraphs.ORDER_REPOSITORY.value()),
                        RelationType.CALL, List.of(Evidence.of("java", "service method calls repository method", ExtractionFidelity.L2_SYMBOL_RESOLVED))),
                new EdgeFact(SymbolRef.of(CanonicalSampleGraphs.ORDER_CONTROLLER.value()), SymbolRef.of(CanonicalSampleGraphs.ORDER_REPOSITORY.value()),
                        RelationType.CALL, List.of(Evidence.of("java", "controller method calls repository method directly", ExtractionFidelity.L2_SYMBOL_RESOLVED))),
                new EdgeFact(SymbolRef.of(CanonicalSampleGraphs.ORDER_REPOSITORY.value()),
                        SymbolRef.of("external-jdbc-datasource", "external JDBC datasource, not modeled as a node"),
                        RelationType.COMMUNICATES, List.of(Evidence.of("spring-data", "repository backed by a DataSource", ExtractionFidelity.L1_SYNTAX))));

        Graph assembled = assembler.assemble(nodeFacts, edgeFacts);
        Graph expected = CanonicalSampleGraphs.layeredOrderSlice();

        // GraphAssembler orders its output canonically (by id / by a
        // canonical edge key), which is not the same as the fixture's
        // hand-authored insertion order - both are internally deterministic,
        // but not to each other, so compare as sets/sorted, not as lists.
        assertEquals(nodeSnapshots(expected).stream().sorted().toList(),
                nodeSnapshots(assembled).stream().sorted().toList(),
                "roles aside, GraphAssembler must reproduce the fixture's node shape exactly");
        assertEquals(new java.util.HashSet<>(expected.edges()), new java.util.HashSet<>(assembled.edges()));
        assertEquals(expected.edges().size(), assembled.edges().size(), "no edge should be lost or duplicated");
    }

    @Test
    void extractionResultFlowsThroughASourceExtractorIntoAnAssembledGraph() {
        ExtractionResult canned = ExtractionResult.of(
                List.of(node("a", NodeType.COMPONENT), node("b", NodeType.COMPONENT)),
                List.of(new EdgeFact(SymbolRef.of("a"), SymbolRef.of("b"), RelationType.CALL, List.of())));
        SourceExtractor extractor = request -> canned;

        ExtractionResult result = extractor.extract(ExtractionRequest.of(List.of(java.nio.file.Path.of("src"))));
        Graph graph = assembler.assemble(result);

        assertEquals(2, graph.nodes().size());
        assertTrue(graph.node(NodeId.of("a")).isPresent());
    }
}
