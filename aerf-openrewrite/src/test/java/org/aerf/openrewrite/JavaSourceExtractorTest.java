package org.aerf.openrewrite;

import org.aerf.analysis.role.SeedRoleInferenceEngine;
import org.aerf.analysis.role.seed.DefaultSeedRules;
import org.aerf.extraction.ExtractionRequest;
import org.aerf.extraction.ExtractionResult;
import org.aerf.extraction.GraphAssembler;
import org.aerf.extraction.JavaNodeIds;
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
 * Exercises {@link JavaSourceExtractor} against the sample project
 * fixture under {@code src/test/resources/sample-project}, both without a
 * classpath (§8.1's L1 degradation path — the only mode a within-batch
 * JDK type still resolves in) and with one, per the ExtractionAdapter
 * Plan's explicit "test both modes" instruction. The fixture's own
 * comments explain each planted case; {@link LegacyWidget}'s deliberately
 * undeclared supertype and {@code OrderService.touchLegacyWidget()}'s
 * call on it are the L1_SYNTAX fixtures for EXTENDS and CALL
 * respectively; {@code OrderService.reprocessAll(...)} is the ITERATED
 * execution-context fixture.
 */
class JavaSourceExtractorTest {

    private static Path sampleProjectRoot() {
        try {
            URL url = JavaSourceExtractorTest.class.getClassLoader().getResource("sample-project");
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("sample-project resource not found", e);
        }
    }

    private static Graph extractSample(List<Path> classpath) {
        JavaSourceExtractor extractor = new JavaSourceExtractor();
        ExtractionRequest request = new ExtractionRequest(List.of(sampleProjectRoot()), classpath);
        ExtractionResult result = extractor.extract(request);
        return new GraphAssembler().assemble(result);
    }

    @Test
    void extractsAllApplicationComponentNodesWithoutAClasspath() {
        // 12 total: the 8 com.example.* types below plus the 3 local
        // org.springframework.stereotype.* annotation-type stubs
        // (Controller/Service/Repository) and the 1 local
        // org.springframework.data.repository.Repository marker-interface
        // stub - an annotation type declaration is itself a
        // J.ClassDeclaration (Kind.Type.Annotation), so it correctly gets
        // its own COMPONENT node too, incidental to this increment's real
        // purpose but not filtered out (section 8: this extractor doesn't
        // get to decide a real declared type in the batch is
        // uninteresting).
        Graph graph = extractSample(List.of());

        assertEquals(12, graph.nodes().stream().filter(n -> n.type() == NodeType.COMPONENT).count());
        for (String fqn : List.of("com.example.BaseEntity", "com.example.Order",
                "com.example.OrderRepository", "com.example.OrderRepositoryImpl", "com.example.OrderQueryRepository",
                "com.example.LegacyWidget", "com.example.OrderService", "com.example.OrderController")) {
            NodeId id = JavaNodeIds.type(fqn);
            Optional<Node> node = graph.node(id);
            assertTrue(node.isPresent(), fqn + " should have been extracted as a COMPONENT node");
            assertEquals(NodeType.COMPONENT, node.get().type());
        }
    }

    @Test
    void resolvesWithinBatchExtendsAndImplementsEvenWithoutAnExternalClasspath() {
        Graph graph = extractSample(List.of());

        assertResolvedTypeEdge(graph, "com.example.Order", "com.example.BaseEntity", RelationType.EXTENDS,
                ExtractionFidelity.L2_SYMBOL_RESOLVED);
        assertResolvedTypeEdge(graph, "com.example.OrderRepositoryImpl", "com.example.OrderRepository",
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
    void extractsFunctionNodesForDeclaredMethods() {
        Graph graph = extractSample(List.of());

        NodeId placeOrderId = JavaNodeIds.method("com.example.OrderService", "placeOrder", List.of("java.lang.Long"));
        NodeId findByIdId = JavaNodeIds.method("com.example.OrderRepository", "findById", List.of("java.lang.Long"));
        for (NodeId id : List.of(placeOrderId, findByIdId)) {
            Optional<Node> node = graph.node(id);
            assertTrue(node.isPresent(), id + " should have been extracted as a FUNCTION node");
            assertEquals(NodeType.FUNCTION, node.get().type());
        }
    }

    @Test
    void emitsAMemberOfEdgeFromEachMethodToItsDeclaringClass() {
        // Open question #17 / AERF v0.4.1 patch Amendment 6: a purely
        // structural fact, always resolved (a method's declaring class is
        // always in the same parse batch) - deliberately not consumed by
        // role inference itself, see the amendment for why.
        Graph graph = extractSample(List.of());

        NodeId placeOrderId = JavaNodeIds.method("com.example.OrderService", "placeOrder", List.of("java.lang.Long"));
        NodeId orderServiceId = JavaNodeIds.type("com.example.OrderService");
        List<org.aerf.model.Edge> memberOfEdges = graph.edgesFrom(placeOrderId).stream()
                .filter(e -> e.relation() == RelationType.MEMBER_OF)
                .toList();
        assertEquals(1, memberOfEdges.size());
        org.aerf.model.Edge edge = memberOfEdges.get(0);
        assertTrue(edge.target() instanceof NodeRef.Resolved resolved && resolved.id().equals(orderServiceId));
    }

    @Test
    void resolvesADependsEdgeFromAFieldDeclarationWithinTheBatch() {
        Graph graph = extractSample(List.of());

        assertResolvedTypeEdge(graph, "com.example.OrderService", "com.example.OrderRepository", RelationType.DEPENDS,
                ExtractionFidelity.L2_SYMBOL_RESOLVED);
        assertResolvedTypeEdge(graph, "com.example.OrderService", "com.example.LegacyWidget", RelationType.DEPENDS,
                ExtractionFidelity.L2_SYMBOL_RESOLVED);
    }

    @Test
    void propagatesAClasssStereotypeEvidenceOntoItsOwnDeclaredMethods() {
        // Increment 16: the canonical graph has no structural edge from a
        // FUNCTION node to its declaring COMPONENT node, so a method's
        // own role can never come from graph-relationship refinement -
        // only from evidence on the method's own NodeFact. Copying the
        // class's stereotype evidence onto each declared method is what
        // makes a method-level role (and therefore layering/N+1 checks
        // that look at a CALL edge's own endpoints) inferable at all.
        Graph graph = extractSample(List.of());

        NodeId placeOrderId = JavaNodeIds.method("com.example.OrderService", "placeOrder", List.of("java.lang.Long"));
        Node placeOrder = graph.node(placeOrderId).orElseThrow();
        assertTrue(placeOrder.evidence().stream().anyMatch(e -> e.sourceAdapter().equals("spring")
                && "org.springframework.stereotype.Service".equals(e.attributes().get("annotation"))));
    }

    @Test
    void emitsStructuredEvidenceMatchingDefaultSeedRulesForEachSpringStereotype() {
        Graph graph = extractSample(List.of());

        Node controller = graph.node(JavaNodeIds.type("com.example.OrderController")).orElseThrow();
        assertTrue(controller.evidence().stream().anyMatch(e -> e.sourceAdapter().equals("spring")
                && "org.springframework.stereotype.Controller".equals(e.attributes().get("annotation"))));

        Node service = graph.node(JavaNodeIds.type("com.example.OrderService")).orElseThrow();
        assertTrue(service.evidence().stream().anyMatch(e -> e.sourceAdapter().equals("spring")
                && "org.springframework.stereotype.Service".equals(e.attributes().get("annotation"))));

        Node repositoryImpl = graph.node(JavaNodeIds.type("com.example.OrderRepositoryImpl")).orElseThrow();
        assertTrue(repositoryImpl.evidence().stream().anyMatch(e -> e.sourceAdapter().equals("spring-data")
                && "org.springframework.stereotype.Repository".equals(e.attributes().get("annotation"))));
    }

    @Test
    void recognizesASpringDataRepositoryByMarkerInterfaceWithNoAnnotationAtAll() {
        // Open question #18: OrderQueryRepository extends
        // org.springframework.data.repository.Repository directly - no
        // @Repository annotation anywhere - exactly the real-world shape
        // Increment 18's spring-petclinic run found invisible to the
        // annotation-only rule. This is the positive case; the negative
        // control (an @Repository-annotated impl carrying no marker
        // interface) is OrderRepositoryImpl in the test above.
        Graph graph = extractSample(List.of());

        Node repository = graph.node(JavaNodeIds.type("com.example.OrderQueryRepository")).orElseThrow();
        assertTrue(repository.evidence().stream().anyMatch(e -> e.sourceAdapter().equals("spring-data")
                && "org.springframework.data.repository.Repository".equals(e.attributes().get("springDataMarkerInterface"))));

        // Propagated onto the declared method too, same as annotation-based
        // stereotype evidence (Increment 16's evidence-copy substitute for
        // open question #17's missing declares/member-of relation).
        NodeId findByStatusId = JavaNodeIds.method("com.example.OrderQueryRepository", "findByStatus", List.of("java.lang.String"));
        Node findByStatus = graph.node(findByStatusId).orElseThrow();
        assertTrue(findByStatus.evidence().stream().anyMatch(e -> e.sourceAdapter().equals("spring-data")));
    }

    @Test
    void doesNotEmitStereotypeEvidenceForATypeWithNoSpringAnnotation() {
        // BaseEntity carries no Spring stereotype annotation at all - the
        // negative control for the previous test, so a false positive
        // (evidence appearing regardless of what's actually annotated)
        // wouldn't slip by unnoticed.
        Graph graph = extractSample(List.of());

        Node baseEntity = graph.node(JavaNodeIds.type("com.example.BaseEntity")).orElseThrow();
        assertTrue(baseEntity.evidence().stream().noneMatch(e -> e.sourceAdapter().equals("spring")
                || e.sourceAdapter().equals("spring-data")));
    }

    @Test
    void realExtractedEvidenceDrivesSeedRoleInferenceEndToEnd() {
        // The payoff this increment exists for: DefaultSeedRules (written
        // in Increment 2, long before any real adapter existed) correctly
        // classifies real, parsed, annotation-driven evidence with no
        // rule-side change at all - open question #1 ("when does
        // seed-only role classification actually fail?") finally has real
        // data to run against, even though this one sample doesn't happen
        // to expose a failure case itself.
        Graph graph = extractSample(List.of());
        SeedRoleInferenceEngine engine = new SeedRoleInferenceEngine(DefaultSeedRules.illustrativeRules());
        Graph withRoles = engine.inferAndApply(graph);

        assertEquals(Role.PRESENTATION, withRoles.node(JavaNodeIds.type("com.example.OrderController")).orElseThrow().role());
        assertEquals(Role.APPLICATION, withRoles.node(JavaNodeIds.type("com.example.OrderService")).orElseThrow().role());
        assertEquals(Role.PERSISTENCE, withRoles.node(JavaNodeIds.type("com.example.OrderRepositoryImpl")).orElseThrow().role());
        // Open question #18: classified PERSISTENCE from marker-interface
        // evidence alone, with no @Repository annotation on this type at
        // all - the real failure case Increment 18 found, now fixed.
        assertEquals(Role.PERSISTENCE, withRoles.node(JavaNodeIds.type("com.example.OrderQueryRepository")).orElseThrow().role());
        assertEquals(Role.UNKNOWN, withRoles.node(JavaNodeIds.type("com.example.BaseEntity")).orElseThrow().role());
    }

    @Test
    void resolvesACallEdgeToAnInBatchMethodWithoutIteratedContextOutsideALoop() {
        Graph graph = extractSample(List.of());

        NodeId placeOrderId = JavaNodeIds.method("com.example.OrderService", "placeOrder", List.of("java.lang.Long"));
        NodeId findByIdId = JavaNodeIds.method("com.example.OrderRepository", "findById", List.of("java.lang.Long"));

        List<org.aerf.model.Edge> callEdges = graph.edgesFrom(placeOrderId).stream()
                .filter(e -> e.relation() == RelationType.CALL)
                .toList();
        assertEquals(1, callEdges.size());
        org.aerf.model.Edge edge = callEdges.get(0);
        assertTrue(edge.target() instanceof NodeRef.Resolved resolved && resolved.id().equals(findByIdId));
        assertEquals(ExecutionContext.UNKNOWN, edge.provenance().get(0).executionContext());
    }

    @Test
    void marksACallEdgeInsideAForEachLoopAsIterated() {
        Graph graph = extractSample(List.of());

        NodeId reprocessAllId = JavaNodeIds.method("com.example.OrderService", "reprocessAll", List.of("java.util.List"));
        NodeId findByIdId = JavaNodeIds.method("com.example.OrderRepository", "findById", List.of("java.lang.Long"));

        List<org.aerf.model.Edge> callEdges = graph.edgesFrom(reprocessAllId).stream()
                .filter(e -> e.relation() == RelationType.CALL)
                .toList();
        assertEquals(1, callEdges.size());
        org.aerf.model.Edge edge = callEdges.get(0);
        assertTrue(edge.target() instanceof NodeRef.Resolved resolved && resolved.id().equals(findByIdId));
        assertEquals(ExecutionContext.ITERATED, edge.provenance().get(0).executionContext());
    }

    @Test
    void recordsACallToAnUnresolvableMethodAsL1SyntaxUnresolved() {
        Graph graph = extractSample(List.of());

        NodeId touchLegacyWidgetId = JavaNodeIds.method("com.example.OrderService", "touchLegacyWidget", List.of());
        List<org.aerf.model.Edge> callEdges = graph.edgesFrom(touchLegacyWidgetId).stream()
                .filter(e -> e.relation() == RelationType.CALL)
                .toList();
        assertEquals(1, callEdges.size());
        org.aerf.model.Edge edge = callEdges.get(0);
        assertTrue(edge.target() instanceof NodeRef.Unresolved unresolved
                && unresolved.description().equals("legacyOperation"));
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

        assertResolvedTypeEdge(graph, "com.example.Order", "com.example.BaseEntity", RelationType.EXTENDS,
                ExtractionFidelity.L2_SYMBOL_RESOLVED);
        assertResolvedTypeEdge(graph, "com.example.OrderRepositoryImpl", "com.example.OrderRepository",
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

    private static void assertResolvedTypeEdge(Graph graph, String fromFqn, String toFqn, RelationType relation,
            ExtractionFidelity expectedFidelity) {
        NodeId fromId = JavaNodeIds.type(fromFqn);
        NodeId toId = JavaNodeIds.type(toFqn);
        List<org.aerf.model.Edge> matching = graph.edgesFrom(fromId).stream()
                .filter(e -> e.relation() == relation && e.target() instanceof NodeRef.Resolved resolved
                        && resolved.id().equals(toId))
                .toList();
        assertEquals(1, matching.size(), () -> fromFqn + " should have exactly one " + relation + " edge to " + toFqn);
        org.aerf.model.Edge edge = matching.get(0);
        assertFalse(edge.provenance().isEmpty());
        assertEquals(expectedFidelity, edge.provenance().get(0).fidelity());
    }
}
