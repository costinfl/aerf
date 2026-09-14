package org.aerf.analysis.metrics.layer;

import org.aerf.analysis.governance.SubsystemLayerPolicies;
import org.aerf.analysis.governance.SubsystemLayerPolicy;
import org.aerf.model.Graph;
import org.aerf.model.Node;
import org.aerf.model.NodeId;
import org.aerf.model.NodeRef;
import org.aerf.model.NodeType;
import org.aerf.model.RelationType;
import org.aerf.model.Role;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Increment 26 (OQ-04): per-subsystem layering matrices. See
 * {@code docs/increment-26-per-subsystem-layer-matrices.md}.
 *
 * <p>The fixture is two subsystems, each with a Controller calling a
 * Repository directly — the classic Presentation-to-Persistence shape.
 * {@code strict} forbids it; {@code legacy} permits it, standing in for a
 * subsystem that "evolved in a different era", which is the motivation
 * OQ-04 was raised with.
 */
class LayerEntropyCalculatorSubsystemTest {

    private static final LayerPolicy STRICT = LayerPolicy.of(
            Set.of(Role.PRESENTATION, Role.APPLICATION, Role.PERSISTENCE),
            Map.of(
                    Role.PRESENTATION, Set.of(Role.PRESENTATION, Role.APPLICATION),
                    Role.APPLICATION, Set.of(Role.APPLICATION, Role.PERSISTENCE),
                    Role.PERSISTENCE, Set.of(Role.PERSISTENCE)));

    /** Same roles, but a direct Presentation to Persistence call is tolerated. */
    private static final LayerPolicy LEGACY = LayerPolicy.of(
            Set.of(Role.PRESENTATION, Role.APPLICATION, Role.PERSISTENCE),
            Map.of(
                    Role.PRESENTATION, Set.of(Role.PRESENTATION, Role.APPLICATION, Role.PERSISTENCE),
                    Role.APPLICATION, Set.of(Role.APPLICATION, Role.PERSISTENCE),
                    Role.PERSISTENCE, Set.of(Role.PERSISTENCE)));

    @Test
    void noDeclaredSubsystemsIsIdenticalToTheSinglePolicyBehaviour() {
        // OQ-04's acceptance criterion: "existing single-policy behaviour
        // remains reproducible". Satisfied by construction rather than by
        // proof - an empty declaration takes the same code path - and
        // asserted directly here against the pre-OQ-04 constructor.
        Graph graph = twoSubsystems();

        LayerEntropyResult withoutSubsystems =
                LayerEntropyCalculator.withCallAndDependsRelations(STRICT).compute(graph);
        LayerEntropyResult withEmptyDeclaration = LayerEntropyCalculator
                .withCallAndDependsRelations(STRICT, SubsystemLayerPolicies.none()).compute(graph);

        assertEquals(withoutSubsystems.relevantEdges(), withEmptyDeclaration.relevantEdges());
        assertEquals(withoutSubsystems.violatingEdges(), withEmptyDeclaration.violatingEdges());
        assertEquals(withoutSubsystems.value(), withEmptyDeclaration.value());
        assertEquals(OptionalDouble.of(1.0), withEmptyDeclaration.value(),
                "both controller-to-repository calls violate the strict matrix");
    }

    @Test
    void eachSubsystemsOwnMatrixJudgesItsOwnInternalEdges() {
        // The whole point of OQ-04: the same edge shape, violating in one
        // subsystem and tolerated in another.
        LayerEntropyResult result = LayerEntropyCalculator
                .withCallAndDependsRelations(STRICT, SubsystemLayerPolicies.of(List.of(
                        new SubsystemLayerPolicy("strict-era", "com.example.billing", STRICT),
                        new SubsystemLayerPolicy("legacy-era", "com.example.shipping", LEGACY))))
                .compute(twoSubsystems());

        assertEquals(2, result.relevantEdges().size(), "both calls remain measurable");
        assertEquals(1, result.violatingEdges().size());
        assertEquals(OptionalDouble.of(0.5), result.value());
        assertEquals(NodeRef.resolved(NodeId.of("com.example.billing.InvoiceController")),
                result.violatingEdges().get(0).source(),
                "only the subsystem whose matrix forbids it is in violation");
    }

    @Test
    void aNodeInNoDeclaredSubsystemIsGovernedByTheDefaultMatrix() {
        LayerEntropyResult result = LayerEntropyCalculator
                .withCallAndDependsRelations(LEGACY, SubsystemLayerPolicies.of(List.of(
                        new SubsystemLayerPolicy("strict-era", "com.example.billing", STRICT))))
                .compute(twoSubsystems());

        assertEquals(2, result.relevantEdges().size());
        assertEquals(1, result.violatingEdges().size(),
                "billing is judged by its own strict matrix; shipping falls back to the permissive default");
        assertEquals(NodeRef.resolved(NodeId.of("com.example.billing.InvoiceController")),
                result.violatingEdges().get(0).source());
    }

    @Test
    void aCrossSubsystemEdgeIsGovernedByItsSourcesMatrix() {
        // Decided on principle, and tested synthetically: no real
        // repository in this project's evidence base has a cross-subsystem
        // layer edge at all (every relevant edge in the petclinic scan is
        // intra-package). Layering constrains what a component may depend
        // on, so the source is the party whose declared rules are tested.
        Graph graph = Graph.builder()
                .addNode(node("com.example.billing.InvoiceController", Role.PRESENTATION))
                .addNode(node("com.example.shipping.CrateRepository", Role.PERSISTENCE))
                .addEdge(ref("com.example.billing.InvoiceController"),
                        ref("com.example.shipping.CrateRepository"), RelationType.CALL, List.of())
                .build();

        SubsystemLayerPolicy strictBilling = new SubsystemLayerPolicy("billing", "com.example.billing", STRICT);
        SubsystemLayerPolicy legacyShipping = new SubsystemLayerPolicy("shipping", "com.example.shipping", LEGACY);

        assertEquals(OptionalDouble.of(1.0), LayerEntropyCalculator
                        .withCallAndDependsRelations(LEGACY,
                                SubsystemLayerPolicies.of(List.of(strictBilling, legacyShipping)))
                        .compute(graph).value(),
                "the source's strict matrix judges it, not the target's permissive one");

        assertEquals(OptionalDouble.of(0.0), LayerEntropyCalculator
                        .withCallAndDependsRelations(STRICT,
                                SubsystemLayerPolicies.of(List.of(
                                        new SubsystemLayerPolicy("billing", "com.example.billing", LEGACY),
                                        new SubsystemLayerPolicy("shipping", "com.example.shipping", STRICT))))
                        .compute(graph).value(),
                "and symmetrically: a permissive source tolerates it however strict the target is");
    }

    @Test
    void aCrossSubsystemEdgeIsNeverDroppedFromTheDenominator() {
        // The anti-regression pin. Excluding cross-boundary traffic would
        // shrink the denominator precisely where the most interesting
        // violations live, hiding them behind a better-looking ratio.
        Graph graph = Graph.builder()
                .addNode(node("com.example.billing.InvoiceController", Role.PRESENTATION))
                .addNode(node("com.example.shipping.CrateRepository", Role.PERSISTENCE))
                .addEdge(ref("com.example.billing.InvoiceController"),
                        ref("com.example.shipping.CrateRepository"), RelationType.CALL, List.of())
                .build();

        LayerEntropyResult result = LayerEntropyCalculator
                .withCallAndDependsRelations(STRICT, SubsystemLayerPolicies.of(List.of(
                        new SubsystemLayerPolicy("billing", "com.example.billing", STRICT),
                        new SubsystemLayerPolicy("shipping", "com.example.shipping", STRICT))))
                .compute(graph);

        assertEquals(1, result.relevantEdges().size(),
                "an edge is always governed by exactly one matrix - never by none, never by two");
    }

    @Test
    void anEdgeWhoseGoverningMatrixDoesNotKnowARoleIsExcludedFromBothSides() {
        // The relevance filter uses the governing policy, not the default:
        // whether an edge is measurable at all is a question only the
        // matrix actually judging it can answer.
        LayerPolicy knowsNoPersistence = LayerPolicy.of(
                Set.of(Role.PRESENTATION, Role.APPLICATION),
                Map.of(Role.PRESENTATION, Set.of(Role.PRESENTATION, Role.APPLICATION)));

        LayerEntropyResult result = LayerEntropyCalculator
                .withCallAndDependsRelations(STRICT, SubsystemLayerPolicies.of(List.of(
                        new SubsystemLayerPolicy("billing", "com.example.billing", knowsNoPersistence))))
                .compute(twoSubsystems());

        assertEquals(1, result.relevantEdges().size(),
                "billing's call leaves the measurement universe entirely; shipping's remains");
        assertTrue(result.relevantEdges().get(0).source().toString().contains("shipping"),
                result.relevantEdges().toString());
    }

    @Test
    void subsystemDeclarationDoesNotChangeWhichEdgesAreInRelationScope() {
        // Confidence measures the relation set without the role/policy
        // filter (Amendment 7), so it must be identical however many
        // matrices are declared.
        Graph graph = twoSubsystems();

        assertEquals(
                LayerEntropyCalculator.withCallAndDependsRelations(STRICT).confidence(graph),
                LayerEntropyCalculator.withCallAndDependsRelations(STRICT, SubsystemLayerPolicies.of(List.of(
                        new SubsystemLayerPolicy("billing", "com.example.billing", LEGACY)))).confidence(graph));
    }

    @Test
    void noOrderingDerivedFallbackIsIntroduced() {
        // OQ-04's acceptance: "no ordering-derived fallback is silently
        // introduced". A subsystem without a declared matrix is
        // unrepresentable - SubsystemLayerPolicy requires one - and
        // nothing here builds a matrix from a role ordering.
        assertEquals(3, SubsystemLayerPolicy.class.getRecordComponents().length);
        for (Method method : LayerEntropyCalculator.class.getDeclaredMethods()) {
            for (Class<?> parameter : method.getParameterTypes()) {
                assertFalse(List.class.isAssignableFrom(parameter),
                        "a List parameter here could carry an ordered role list to derive from: " + method);
            }
        }
    }

    /**
     * Two subsystems, each with a Presentation node calling a Persistence
     * node directly.
     */
    private static Graph twoSubsystems() {
        return Graph.builder()
                .addNode(node("com.example.billing.InvoiceController", Role.PRESENTATION))
                .addNode(node("com.example.billing.InvoiceRepository", Role.PERSISTENCE))
                .addNode(node("com.example.shipping.CrateController", Role.PRESENTATION))
                .addNode(node("com.example.shipping.CrateRepository", Role.PERSISTENCE))
                .addEdge(ref("com.example.billing.InvoiceController"),
                        ref("com.example.billing.InvoiceRepository"), RelationType.CALL, List.of())
                .addEdge(ref("com.example.shipping.CrateController"),
                        ref("com.example.shipping.CrateRepository"), RelationType.CALL, List.of())
                .build();
    }

    private static Node node(String id, Role role) {
        return Node.of(NodeId.of(id), NodeType.COMPONENT, role, Map.of(), List.of());
    }

    private static NodeRef ref(String id) {
        return NodeRef.resolved(NodeId.of(id));
    }
}
