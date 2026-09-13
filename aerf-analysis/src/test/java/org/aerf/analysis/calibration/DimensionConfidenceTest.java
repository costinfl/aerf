package org.aerf.analysis.calibration;

import org.aerf.analysis.metrics.cycle.CycleEntropyCalculator;
import org.aerf.analysis.metrics.layer.LayerEntropyCalculator;
import org.aerf.analysis.metrics.layer.LayerPolicy;
import org.aerf.analysis.metrics.persistence.PersistenceEntropyCalculator;
import org.aerf.analysis.metrics.security.SecurityEntropyCalculator;
import org.aerf.analysis.metrics.security.rules.DefaultSecurityRules;
import org.aerf.model.Graph;
import org.aerf.model.Node;
import org.aerf.model.NodeId;
import org.aerf.model.NodeRef;
import org.aerf.model.NodeType;
import org.aerf.model.RelationType;
import org.aerf.model.Role;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OQ-13 (post-v0.4.1 backlog): confidence scoped to one dimension's own
 * relevant evidence. See
 * {@code docs/increment-24-per-dimension-confidence.md}.
 */
class DimensionConfidenceTest {

    private static Node node(String id) {
        return Node.of(NodeId.of(id), NodeType.COMPONENT, Role.UNKNOWN, Map.of(), List.of());
    }

    private static Graph.Builder twoNodes() {
        return Graph.builder().addNode(node("a")).addNode(node("b"));
    }

    private static NodeRef to(String id) {
        return NodeRef.resolved(NodeId.of(id));
    }

    @Test
    void allInScopeEdgesResolvedGivesOne() {
        Graph graph = twoNodes()
                .addEdge(to("a"), to("b"), RelationType.CALL, List.of())
                .build();

        assertEquals(OptionalDouble.of(1.0),
                DimensionConfidence.forRelations(graph, EnumSet.of(RelationType.CALL)));
    }

    @Test
    void noInScopeEdgeResolvedGivesZeroWhichIsAMeasurementNotAnAbsence() {
        Graph graph = twoNodes()
                .addEdge(to("a"), NodeRef.unresolved("dynamic target"), RelationType.CALL, List.of())
                .build();

        assertEquals(OptionalDouble.of(0.0),
                DimensionConfidence.forRelations(graph, EnumSet.of(RelationType.CALL)),
                "0.0 means every reference this dimension measured failed to resolve - a real result");
    }

    @Test
    void oneUnresolvedOfTwoGivesOneHalf() {
        Graph graph = twoNodes()
                .addEdge(to("a"), to("b"), RelationType.CALL, List.of())
                .addEdge(to("a"), NodeRef.unresolved("dynamic target"), RelationType.CALL, List.of())
                .build();

        assertEquals(OptionalDouble.of(0.5),
                DimensionConfidence.forRelations(graph, EnumSet.of(RelationType.CALL)));
    }

    @Test
    void anEmptyScopeIsUndefinedNotOne() {
        Graph graph = twoNodes()
                .addEdge(to("a"), to("b"), RelationType.DEPENDS, List.of())
                .build();

        assertTrue(DimensionConfidence.forRelations(graph, EnumSet.of(RelationType.CALL)).isEmpty(),
                "no CALL edges at all means nothing was measurable, which is not the same as full confidence");
    }

    @Test
    void outOfScopeRelationsMoveNeitherNumeratorNorDenominator() {
        // The denominator regression check the backlog's section 10.3
        // demands: adding evidence in another relation must not change a
        // dimension's confidence at all.
        Graph withoutNoise = twoNodes()
                .addEdge(to("a"), to("b"), RelationType.CALL, List.of())
                .addEdge(to("a"), NodeRef.unresolved("x"), RelationType.CALL, List.of())
                .build();
        Graph withNoise = twoNodes()
                .addEdge(to("a"), to("b"), RelationType.CALL, List.of())
                .addEdge(to("a"), NodeRef.unresolved("x"), RelationType.CALL, List.of())
                .addEdge(to("a"), NodeRef.unresolved("y"), RelationType.DEPENDS, List.of())
                .addEdge(to("b"), to("a"), RelationType.EXTENDS, List.of())
                .build();

        Set<RelationType> callOnly = EnumSet.of(RelationType.CALL);
        assertEquals(DimensionConfidence.forRelations(withoutNoise, callOnly),
                DimensionConfidence.forRelations(withNoise, callOnly));
    }

    @Test
    void memberOfEdgesEnterNoDimensionsDenominator() {
        // Increment 21 added MEMBER_OF and it silently moved graph-wide
        // confidence (0.714 -> 0.857) because those edges are resolved by
        // construction. AnalysisConfidence now excludes it explicitly.
        // Per-dimension confidence excludes it structurally instead - no
        // dimension's relation set contains it - so this pins that the
        // exclusion is real rather than incidental.
        Graph graph = twoNodes()
                .addEdge(to("a"), NodeRef.unresolved("dynamic target"), RelationType.CALL, List.of())
                .addEdge(to("a"), to("b"), RelationType.MEMBER_OF, List.of())
                .addEdge(to("b"), to("a"), RelationType.MEMBER_OF, List.of())
                .build();

        assertEquals(OptionalDouble.of(0.0),
                PersistenceEntropyCalculator.withCallRelation().confidence(graph),
                "two always-resolved MEMBER_OF edges must not lift this off zero");
        assertEquals(OptionalDouble.of(0.0),
                LayerEntropyCalculator.withCallAndDependsRelations(policy()).confidence(graph));
        assertEquals(OptionalDouble.of(0.0),
                CycleEntropyCalculator.withCallAndDependsRelations(false).confidence(graph));
    }

    @Test
    void persistenceConfidenceIgnoresTheTargetRoleFilterSoItIsNotTriviallyOne() {
        // If the dimension's role filter were folded into the denominator,
        // this would read 1.0: an edge cannot be selected by its target's
        // role unless the target resolved. Measuring the relation scope
        // instead keeps the unresolved call visible.
        Graph graph = Graph.builder()
                .addNode(Node.of(NodeId.of("svc"), NodeType.COMPONENT, Role.APPLICATION, Map.of(), List.of()))
                .addNode(Node.of(NodeId.of("repo"), NodeType.COMPONENT, Role.PERSISTENCE, Map.of(), List.of()))
                .addEdge(to("svc"), to("repo"), RelationType.CALL, List.of())
                .addEdge(to("svc"), NodeRef.unresolved("unknown repository method"), RelationType.CALL, List.of())
                .build();

        PersistenceEntropyCalculator calculator = PersistenceEntropyCalculator.withCallRelation();

        assertEquals(1, calculator.compute(graph).relevantEdges().size(),
                "only the resolved PERSISTENCE-targeted call is a relevant persistence context");
        assertEquals(OptionalDouble.of(0.5), calculator.confidence(graph),
                "but confidence measures both CALL edges, so the unresolved one is not hidden");
    }

    @Test
    void layerConfidenceIgnoresTheRolePolicyFilter() {
        // AERF v0.4.1 Amendment 7: a node's role outcome must never affect
        // confidence. An edge between roles the policy does not know is
        // excluded from layer *entropy*, but still counts here.
        Graph graph = Graph.builder()
                .addNode(Node.of(NodeId.of("ui"), NodeType.COMPONENT, Role.PRESENTATION, Map.of(), List.of()))
                .addNode(Node.of(NodeId.of("ext"), NodeType.COMPONENT, Role.EXTERNAL, Map.of(), List.of()))
                .addEdge(to("ui"), to("ext"), RelationType.CALL, List.of())
                .build();

        LayerEntropyCalculator calculator = LayerEntropyCalculator.withCallAndDependsRelations(policy());

        assertTrue(calculator.compute(graph).relevantEdges().isEmpty(), "EXTERNAL is not in this policy");
        assertEquals(OptionalDouble.of(1.0), calculator.confidence(graph),
                "the reference still resolved, whatever inference made of the roles");
    }

    @Test
    void securityConfidenceIsUndefinedByConstruction() {
        Graph graph = twoNodes()
                .addEdge(to("a"), to("b"), RelationType.CALL, List.of())
                .build();

        assertTrue(new SecurityEntropyCalculator(DefaultSecurityRules.illustrativeRules()).confidence(graph).isEmpty(),
                "security measures over nodes, which are present by construction - there is no "
                        + "resolved/unresolved population to measure, and 1.0 would assert unmeasured certainty");
    }

    @Test
    void graphWideConfidenceIsUnchangedByAnyOfThis() {
        // The acceptance criterion: graph-wide semantics must not move.
        Graph graph = twoNodes()
                .addEdge(to("a"), to("b"), RelationType.CALL, List.of())
                .addEdge(to("a"), NodeRef.unresolved("x"), RelationType.DEPENDS, List.of())
                .build();

        assertEquals(OptionalDouble.of(0.5), AnalysisConfidence.compute(graph),
                "AnalysisConfidence still spans every relation, unchanged by the per-dimension work");
    }

    @Test
    void resultIsDeterministicAcrossRepeatedRuns() {
        Graph graph = twoNodes()
                .addEdge(to("a"), to("b"), RelationType.CALL, List.of())
                .addEdge(to("a"), NodeRef.unresolved("x"), RelationType.CALL, List.of())
                .build();

        Set<RelationType> scope = EnumSet.of(RelationType.CALL);
        assertEquals(DimensionConfidence.forRelations(graph, scope),
                DimensionConfidence.forRelations(graph, scope));
    }

    private static LayerPolicy policy() {
        return LayerPolicy.of(
                Set.of(Role.PRESENTATION, Role.APPLICATION, Role.PERSISTENCE),
                Map.of(
                        Role.PRESENTATION, Set.of(Role.PRESENTATION, Role.APPLICATION),
                        Role.APPLICATION, Set.of(Role.APPLICATION, Role.PERSISTENCE),
                        Role.PERSISTENCE, Set.of(Role.PERSISTENCE)));
    }
}
