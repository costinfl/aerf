package org.aerf.analysis.role;

import org.aerf.analysis.role.seed.DefaultSeedRules;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeedRoleInferenceEngineTest {

    private final SeedRoleInferenceEngine engine = new SeedRoleInferenceEngine(DefaultSeedRules.illustrativeRules());

    @Test
    void nodeWithNoMatchingEvidenceIsUnknownRatherThanGuessed() {
        Node node = Node.withUnknownRole(NodeId.of("legacy.Obscure"), NodeType.FUNCTION, Map.of(), List.of());

        RoleInferenceResult result = engine.inferSeedRole(node);

        assertEquals(Role.UNKNOWN, result.role());
        assertTrue(result.signals().isEmpty());
    }

    @Test
    void inferenceIsDeterministicAcrossRepeatedRuns() {
        Node node = Node.withUnknownRole(NodeId.of("com.example.OrderRepository"), NodeType.COMPONENT, Map.of(),
                List.of(Evidence.of("spring-data", "Repository interface", ExtractionFidelity.L2_SYMBOL_RESOLVED)));

        assertEquals(engine.inferSeedRole(node), engine.inferSeedRole(node));
    }

    @Test
    void inferenceIgnoresAnyPreExistingRoleAlreadyOnTheNode() {
        NodeId id = NodeId.of("com.example.OrderRepository");
        List<Evidence> evidence = List.of(Evidence.of("spring-data", "Repository interface", ExtractionFidelity.L2_SYMBOL_RESOLVED));

        Node markedUnknown = Node.of(id, NodeType.COMPONENT, Role.UNKNOWN, Map.of(), evidence);
        Node markedWrongOnPurpose = Node.of(id, NodeType.COMPONENT, Role.PRESENTATION, Map.of(), evidence);

        Role fromUnknown = engine.inferSeedRole(markedUnknown).role();
        Role fromWrong = engine.inferSeedRole(markedWrongOnPurpose).role();

        assertEquals(Role.PERSISTENCE, fromUnknown);
        assertEquals(fromUnknown, fromWrong, "seed inference must depend only on evidence, never on a node's existing role field");
    }

    @Test
    void conflictingSignalsAreResolvedByTheSection34PrecedenceOrder() {
        // A DATA node (seeds Domain) that also carries spring-data evidence (seeds
        // Persistence). Section 3.4's hypothesis order ranks Persistence above
        // Domain, so Persistence must win while both signals remain visible.
        Node node = Node.withUnknownRole(NodeId.of("legacy.OrderEntity"), NodeType.DATA, Map.of(),
                List.of(Evidence.of("spring-data", "JPA @Entity mapping", ExtractionFidelity.L2_SYMBOL_RESOLVED)));

        RoleInferenceResult result = engine.inferSeedRole(node);

        assertEquals(Role.PERSISTENCE, result.role());
        List<Role> signaledRoles = result.signals().stream().map(RoleSignal::role).toList();
        assertTrue(signaledRoles.contains(Role.PERSISTENCE));
        assertTrue(signaledRoles.contains(Role.DOMAIN));
    }

    @Test
    void seedInferenceIndependentlyReproducesTheFixtureGraphsManuallyAssignedRoles() {
        Graph fixture = CanonicalSampleGraphs.layeredOrderSlice();

        Map<NodeId, RoleInferenceResult> results = engine.inferAll(fixture);

        assertEquals(Role.PRESENTATION, results.get(CanonicalSampleGraphs.ORDER_CONTROLLER).role());
        assertEquals(Role.APPLICATION, results.get(CanonicalSampleGraphs.ORDER_SERVICE).role());
        assertEquals(Role.DOMAIN, results.get(CanonicalSampleGraphs.ORDER).role());
        assertEquals(Role.PERSISTENCE, results.get(CanonicalSampleGraphs.ORDER_REPOSITORY).role());
    }

    @Test
    void inferAndApplyReturnsANewGraphWithRolesReplacedAndEdgesPreserved() {
        Graph fixture = CanonicalSampleGraphs.layeredOrderSlice();

        Graph reRoled = engine.inferAndApply(fixture);

        assertEquals(fixture.edges().size(), reRoled.edges().size());
        assertEquals(Role.PRESENTATION, reRoled.node(CanonicalSampleGraphs.ORDER_CONTROLLER).orElseThrow().role());
        assertEquals(Role.PERSISTENCE, reRoled.node(CanonicalSampleGraphs.ORDER_REPOSITORY).orElseThrow().role());
    }
}
