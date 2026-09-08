package org.aerf.analysis.role;

import org.aerf.analysis.role.graph.DefaultGraphRefinementRules;
import org.aerf.analysis.role.seed.DefaultSeedRules;
import org.aerf.model.Evidence;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IterativeRoleInferenceEngineTest {

    private final IterativeRoleInferenceEngine engine = new IterativeRoleInferenceEngine(
            new SeedRoleInferenceEngine(DefaultSeedRules.illustrativeRules()),
            DefaultGraphRefinementRules.illustrativeRules());

    @Test
    void aTwoHopInheritanceChainNeedsTwoRefinementPassesToFullyResolve() {
        // C is seeded directly (spring-data evidence). B extends C but has no
        // seed evidence of its own. A extends B but has no seed evidence of its
        // own. A single graph-refinement pass only reaches B (since it examines
        // C's role, which is already known from seeding); A only resolves in a
        // second pass, once B has settled. This is the case a single-pass
        // "graph-aware seed" step could not handle, and iteration can.
        NodeId a = NodeId.of("legacy.A");
        NodeId b = NodeId.of("legacy.B");
        NodeId c = NodeId.of("legacy.C");

        Graph graph = Graph.builder()
                .addNode(Node.withUnknownRole(a, NodeType.COMPONENT, Map.of(), List.of()))
                .addNode(Node.withUnknownRole(b, NodeType.COMPONENT, Map.of(), List.of()))
                .addNode(Node.withUnknownRole(c, NodeType.COMPONENT, Map.of(),
                        List.of(Evidence.of("spring-data", "Repository interface", ExtractionFidelity.L2_SYMBOL_RESOLVED))))
                .addEdge(NodeRef.resolved(a), NodeRef.resolved(b), RelationType.EXTENDS, List.of())
                .addEdge(NodeRef.resolved(b), NodeRef.resolved(c), RelationType.EXTENDS, List.of())
                .build();

        IterativeRoleInferenceResult result = engine.infer(graph);

        assertEquals(Role.PERSISTENCE, result.results().get(c).role());
        assertEquals(Role.PERSISTENCE, result.results().get(b).role());
        assertEquals(Role.PERSISTENCE, result.results().get(a).role());
        assertEquals(2, result.passes(), "B resolves in pass 1, A only resolves in pass 2");
    }

    @Test
    void aNodeWithNoSeedEvidenceAndNoResolvableSupertypeStaysUnknown() {
        Graph graph = Graph.builder()
                .addNode(Node.withUnknownRole(NodeId.of("legacy.Orphan"), NodeType.COMPONENT, Map.of(), List.of()))
                .build();

        IterativeRoleInferenceResult result = engine.infer(graph);

        assertEquals(Role.UNKNOWN, result.results().get(NodeId.of("legacy.Orphan")).role());
        assertEquals(0, result.passes(), "nothing to refine, so no pass makes any change");
    }

    @Test
    void refinementNeverOverridesAnAlreadyKnownRoleEvenViaAnUnrelatedSupertype() {
        // Controller is seeded Presentation directly. It also (unusually)
        // extends a Persistence-seeded base class. Refinement must not touch
        // Controller at all, since it was never UNKNOWN in the first place.
        NodeId controller = NodeId.of("web.OrderController");
        NodeId persistenceBase = NodeId.of("legacy.PersistenceBase");

        Graph graph = Graph.builder()
                .addNode(Node.withUnknownRole(controller, NodeType.COMPONENT, Map.of(),
                        List.of(Evidence.of("spring", "@Controller annotation", ExtractionFidelity.L2_SYMBOL_RESOLVED))))
                .addNode(Node.withUnknownRole(persistenceBase, NodeType.COMPONENT, Map.of(),
                        List.of(Evidence.of("spring-data", "Repository interface", ExtractionFidelity.L2_SYMBOL_RESOLVED))))
                .addEdge(NodeRef.resolved(controller), NodeRef.resolved(persistenceBase), RelationType.EXTENDS, List.of())
                .build();

        IterativeRoleInferenceResult result = engine.infer(graph);

        assertEquals(Role.PRESENTATION, result.results().get(controller).role());
    }

    @Test
    void conflictingSupertypeRolesAreResolvedByPrecedenceJustLikeSeedSignals() {
        NodeId unknown = NodeId.of("legacy.Multi");
        NodeId domainBase = NodeId.of("legacy.DomainBase");
        NodeId persistenceBase = NodeId.of("legacy.PersistenceBase");

        Graph graph = Graph.builder()
                .addNode(Node.withUnknownRole(unknown, NodeType.COMPONENT, Map.of(), List.of()))
                .addNode(Node.withUnknownRole(domainBase, NodeType.DATA, Map.of(), List.of()))
                .addNode(Node.withUnknownRole(persistenceBase, NodeType.COMPONENT, Map.of(),
                        List.of(Evidence.of("spring-data", "Repository interface", ExtractionFidelity.L2_SYMBOL_RESOLVED))))
                .addEdge(NodeRef.resolved(unknown), NodeRef.resolved(domainBase), RelationType.EXTENDS, List.of())
                .addEdge(NodeRef.resolved(unknown), NodeRef.resolved(persistenceBase), RelationType.IMPLEMENTS, List.of())
                .build();

        IterativeRoleInferenceResult result = engine.infer(graph);

        // Section 3.4: Persistence outranks Domain.
        assertEquals(Role.PERSISTENCE, result.results().get(unknown).role());
    }

    @Test
    void inferenceIsDeterministicAcrossRepeatedRuns() {
        NodeId a = NodeId.of("legacy.A");
        NodeId b = NodeId.of("legacy.B");
        Graph graph = Graph.builder()
                .addNode(Node.withUnknownRole(a, NodeType.COMPONENT, Map.of(), List.of()))
                .addNode(Node.withUnknownRole(b, NodeType.COMPONENT, Map.of(),
                        List.of(Evidence.of("spring-data", "Repository interface", ExtractionFidelity.L2_SYMBOL_RESOLVED))))
                .addEdge(NodeRef.resolved(a), NodeRef.resolved(b), RelationType.EXTENDS, List.of())
                .build();

        IterativeRoleInferenceResult first = engine.infer(graph);
        IterativeRoleInferenceResult second = engine.infer(graph);

        assertEquals(first.results(), second.results());
        assertEquals(first.passes(), second.passes());
    }

    @Test
    void aLongerChainStillTerminatesWithinTheStructuralBound() {
        List<NodeId> chain = List.of(
                NodeId.of("n0"), NodeId.of("n1"), NodeId.of("n2"), NodeId.of("n3"), NodeId.of("n4"));

        Graph.Builder builder = Graph.builder();
        for (int i = 0; i < chain.size(); i++) {
            List<Evidence> evidence = (i == chain.size() - 1)
                    ? List.of(Evidence.of("spring-data", "Repository interface", ExtractionFidelity.L2_SYMBOL_RESOLVED))
                    : List.of();
            builder.addNode(Node.withUnknownRole(chain.get(i), NodeType.COMPONENT, Map.of(), evidence));
        }
        for (int i = 0; i < chain.size() - 1; i++) {
            builder.addEdge(NodeRef.resolved(chain.get(i)), NodeRef.resolved(chain.get(i + 1)), RelationType.EXTENDS, List.of());
        }

        IterativeRoleInferenceResult result = engine.infer(builder.build());

        for (NodeId id : chain) {
            assertEquals(Role.PERSISTENCE, result.results().get(id).role());
        }
        assertTrue(result.passes() <= chain.size());
    }
}
