package org.aerf.openrewrite;

import org.aerf.extraction.ExtractionRequest;
import org.aerf.extraction.ExtractionResult;
import org.aerf.extraction.GraphAssembler;
import org.aerf.extraction.JavaNodeIds;
import org.aerf.model.ExtractionFidelity;
import org.aerf.model.Graph;
import org.aerf.model.Node;
import org.aerf.model.NodeId;
import org.aerf.model.NodeRef;
import org.aerf.model.NodeType;
import org.aerf.model.RelationType;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link JavaClassExtractor} against the sample project fixture
 * under {@code src/test/resources/sample-project}, both without a
 * classpath (§8.1's L1 degradation path — the only mode a within-batch
 * JDK type still resolves in) and with one, per the ExtractionAdapter
 * Plan's explicit "test both modes" instruction. The fixture's own
 * comments explain each planted case; {@link LegacyWidget}'s deliberately
 * undeclared supertype is the L1_SYNTAX fixture.
 */
class JavaClassExtractorTest {

    private static Path sampleProjectRoot() {
        try {
            URL url = JavaClassExtractorTest.class.getClassLoader().getResource("sample-project");
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("sample-project resource not found", e);
        }
    }

    private static Graph extractSample(List<Path> classpath) {
        JavaClassExtractor extractor = new JavaClassExtractor();
        ExtractionRequest request = new ExtractionRequest(List.of(sampleProjectRoot()), classpath);
        ExtractionResult result = extractor.extract(request);
        return new GraphAssembler().assemble(result);
    }

    @Test
    void extractsAllFiveComponentNodesWithoutAClasspath() {
        Graph graph = extractSample(List.of());

        assertEquals(5, graph.nodes().size());
        for (String fqn : List.of("com.example.BaseEntity", "com.example.Order",
                "com.example.OrderRepository", "com.example.OrderRepositoryImpl", "com.example.LegacyWidget")) {
            NodeId id = JavaNodeIds.type(fqn);
            Optional<Node> node = graph.node(id);
            assertTrue(node.isPresent(), fqn + " should have been extracted as a COMPONENT node");
            assertEquals(NodeType.COMPONENT, node.get().type());
        }
    }

    @Test
    void resolvesWithinBatchExtendsAndImplementsEvenWithoutAnExternalClasspath() {
        Graph graph = extractSample(List.of());

        assertResolvedEdge(graph, "com.example.Order", "com.example.BaseEntity", RelationType.EXTENDS,
                ExtractionFidelity.L2_SYMBOL_RESOLVED);
        assertResolvedEdge(graph, "com.example.OrderRepositoryImpl", "com.example.OrderRepository",
                RelationType.IMPLEMENTS, ExtractionFidelity.L2_SYMBOL_RESOLVED);
    }

    @Test
    void recordsJdkImplementsTargetsAsL2ResolvedFidelityButGraphUnresolved() {
        // java.io.Serializable is a JDK type; OpenRewrite's batch parse
        // resolves its type from the bootstrap classpath even with no
        // external classpath supplied - verified by the Step 0 spike, not
        // assumed - so the extractor records L2_SYMBOL_RESOLVED fidelity
        // for it. But GraphAssembler resolves a SymbolRef only against
        // NodeFacts this run actually declared, and no NodeFact was
        // emitted for java.io.Serializable (it is not part of the
        // extracted source set) - so at the graph level the edge's
        // target is still NodeRef.Unresolved. This is section 8's
        // conservatism, not a bug: an adapter must not fabricate a node
        // for a type it never itself observed a declaration for, however
        // confidently it resolved that type's identity.
        Graph graph = extractSample(List.of());

        NodeId orderId = JavaNodeIds.type("com.example.Order");
        List<org.aerf.model.Edge> implementsEdges = graph.edgesFrom(orderId).stream()
                .filter(e -> e.relation() == RelationType.IMPLEMENTS)
                .toList();
        assertEquals(1, implementsEdges.size());
        org.aerf.model.Edge edge = implementsEdges.get(0);
        assertTrue(edge.target() instanceof NodeRef.Unresolved unresolved
                && unresolved.description().equals("Serializable"));
        assertFalse(edge.provenance().isEmpty());
        assertEquals(ExtractionFidelity.L2_SYMBOL_RESOLVED, edge.provenance().get(0).fidelity());
    }

    @Test
    void recordsTheUndeclaredSupertypeAsUnresolvedL1Syntax() {
        Graph graph = extractSample(List.of());

        NodeId legacyWidgetId = JavaNodeIds.type("com.example.LegacyWidget");
        List<org.aerf.model.Edge> extendsEdges = graph.edgesFrom(legacyWidgetId).stream()
                .filter(e -> e.relation() == RelationType.EXTENDS)
                .toList();
        assertEquals(1, extendsEdges.size());
        org.aerf.model.Edge edge = extendsEdges.get(0);
        assertTrue(edge.target() instanceof NodeRef.Unresolved,
                "com.example.external.UnknownFramework is never declared and not on any classpath");
        assertFalse(edge.provenance().isEmpty());
        assertEquals(ExtractionFidelity.L1_SYNTAX, edge.provenance().get(0).fidelity());
    }

    @Test
    void anExplicitClasspathDoesNotChangeWithinBatchResolutionResults() {
        // The request-level classpath (ExtractionRequest.classpath) exists
        // for third-party dependencies. None of this sample's supertypes
        // are third-party, so supplying an (irrelevant) classpath entry
        // must not change any of the within-batch/JDK resolution results
        // already asserted without one - this only proves the classpath
        // wiring itself doesn't break anything, per the plan's "test both
        // modes" instruction.
        Path irrelevantClasspathEntry = sampleProjectRoot();
        Graph graph = extractSample(List.of(irrelevantClasspathEntry));

        assertResolvedEdge(graph, "com.example.Order", "com.example.BaseEntity", RelationType.EXTENDS,
                ExtractionFidelity.L2_SYMBOL_RESOLVED);
        assertResolvedEdge(graph, "com.example.OrderRepositoryImpl", "com.example.OrderRepository",
                RelationType.IMPLEMENTS, ExtractionFidelity.L2_SYMBOL_RESOLVED);

        NodeId legacyWidgetId = JavaNodeIds.type("com.example.LegacyWidget");
        List<org.aerf.model.Edge> extendsEdges = graph.edgesFrom(legacyWidgetId).stream()
                .filter(e -> e.relation() == RelationType.EXTENDS)
                .toList();
        assertEquals(1, extendsEdges.size());
        assertTrue(extendsEdges.get(0).target() instanceof NodeRef.Unresolved);
    }

    @Test
    void extractionIsDeterministicAcrossRepeatedRuns() {
        Graph first = extractSample(List.of());
        Graph second = extractSample(List.of());

        assertEquals(first.nodes().size(), second.nodes().size());
        assertEquals(first.edges().size(), second.edges().size());
        List<String> firstIds = first.nodes().stream().map(n -> n.id().value()).sorted().toList();
        List<String> secondIds = second.nodes().stream().map(n -> n.id().value()).sorted().toList();
        assertEquals(firstIds, secondIds);
    }

    private static void assertResolvedEdge(Graph graph, String fromFqn, String toFqn, RelationType relation,
            ExtractionFidelity expectedFidelity) {
        NodeId fromId = JavaNodeIds.type(fromFqn);
        List<org.aerf.model.Edge> matching = graph.edgesFrom(fromId).stream()
                .filter(e -> e.relation() == relation)
                .toList();
        assertEquals(1, matching.size(), () -> fromFqn + " should have exactly one " + relation + " edge");
        org.aerf.model.Edge edge = matching.get(0);
        assertTrue(edge.target() instanceof NodeRef.Resolved resolved && resolved.id().value().equals(toFqn),
                () -> "expected " + relation + " target " + toFqn + " but was " + edge.target());
        assertFalse(edge.provenance().isEmpty());
        assertEquals(expectedFidelity, edge.provenance().get(0).fidelity());
    }
}
