package org.aerf.analysis.metrics.persistence;

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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceEntropyCalculatorTest {

    private static Node node(String id, NodeType type, Role role) {
        return Node.of(NodeId.of(id), type, role, Map.of(), List.of());
    }

    private final PersistenceEntropyCalculator calculator = PersistenceEntropyCalculator.withCallRelation();

    @Test
    void anIteratedCallToAPersistenceTargetIsFlagged() {
        Graph graph = Graph.builder()
                .addNode(node("service", NodeType.COMPONENT, Role.APPLICATION))
                .addNode(node("repo", NodeType.COMPONENT, Role.PERSISTENCE))
                .addEdge(NodeRef.resolved(NodeId.of("service")), NodeRef.resolved(NodeId.of("repo")), RelationType.CALL,
                        List.of(Evidence.of("java", "call inside for loop", ExtractionFidelity.L2_SYMBOL_RESOLVED, ExecutionContext.ITERATED)))
                .build();

        PersistenceEntropyResult result = calculator.compute(graph);

        assertEquals(1, result.relevantEdges().size());
        assertEquals(1, result.flaggedEdges().size());
        assertEquals(OptionalDouble.of(1.0), result.value());
    }

    @Test
    void aSingleExecutionCallToAPersistenceTargetIsNotFlagged() {
        Graph graph = Graph.builder()
                .addNode(node("service", NodeType.COMPONENT, Role.APPLICATION))
                .addNode(node("repo", NodeType.COMPONENT, Role.PERSISTENCE))
                .addEdge(NodeRef.resolved(NodeId.of("service")), NodeRef.resolved(NodeId.of("repo")), RelationType.CALL,
                        List.of(Evidence.of("java", "call outside any loop", ExtractionFidelity.L2_SYMBOL_RESOLVED, ExecutionContext.SINGLE)))
                .build();

        PersistenceEntropyResult result = calculator.compute(graph);

        assertEquals(1, result.relevantEdges().size());
        assertTrue(result.flaggedEdges().isEmpty());
        assertEquals(OptionalDouble.of(0.0), result.value());
    }

    @Test
    void evidenceWithNoExecutionContextClaimIsNotFlagged() {
        // Plain Evidence.of(...) without an explicit ExecutionContext defaults to
        // UNKNOWN, which must not be treated as ITERATED (see Evidence's javadoc).
        Graph graph = Graph.builder()
                .addNode(node("service", NodeType.COMPONENT, Role.APPLICATION))
                .addNode(node("repo", NodeType.COMPONENT, Role.PERSISTENCE))
                .addEdge(NodeRef.resolved(NodeId.of("service")), NodeRef.resolved(NodeId.of("repo")), RelationType.CALL,
                        List.of(Evidence.of("java", "call observed", ExtractionFidelity.L1_SYNTAX)))
                .build();

        PersistenceEntropyResult result = calculator.compute(graph);

        assertTrue(result.flaggedEdges().isEmpty());
    }

    @Test
    void callsToNonPersistenceRolesAreExcludedFromRelevantEntirely() {
        Graph graph = Graph.builder()
                .addNode(node("controller", NodeType.COMPONENT, Role.PRESENTATION))
                .addNode(node("service", NodeType.COMPONENT, Role.APPLICATION))
                .addEdge(NodeRef.resolved(NodeId.of("controller")), NodeRef.resolved(NodeId.of("service")), RelationType.CALL,
                        List.of(Evidence.of("java", "call inside for loop", ExtractionFidelity.L2_SYMBOL_RESOLVED, ExecutionContext.ITERATED)))
                .build();

        PersistenceEntropyResult result = calculator.compute(graph);

        assertTrue(result.relevantEdges().isEmpty(), "Application is not Persistence, so this is not a persistence context at all");
    }

    @Test
    void relationsOutsideTheConfiguredSetAreExcluded() {
        Graph graph = Graph.builder()
                .addNode(node("service", NodeType.COMPONENT, Role.APPLICATION))
                .addNode(node("order", NodeType.DATA, Role.PERSISTENCE))
                .addEdge(NodeRef.resolved(NodeId.of("service")), NodeRef.resolved(NodeId.of("order")), RelationType.DEPENDS, List.of())
                .build();

        PersistenceEntropyResult result = calculator.compute(graph);

        assertTrue(result.relevantEdges().isEmpty(), "DEPENDS is not in the default relevant relation set");
    }

    @Test
    void unresolvedTargetsAreExcluded() {
        Graph graph = Graph.builder()
                .addNode(node("service", NodeType.COMPONENT, Role.APPLICATION))
                .addEdge(NodeRef.resolved(NodeId.of("service")), NodeRef.unresolved("dynamic proxy target"), RelationType.CALL, List.of())
                .build();

        PersistenceEntropyResult result = calculator.compute(graph);

        assertTrue(result.relevantEdges().isEmpty());
    }

    @Test
    void graphWithNoRelevantEdgesYieldsAnUndefinedNotZeroValue() {
        Graph graph = Graph.builder()
                .addNode(node("solo", NodeType.COMPONENT, Role.PERSISTENCE))
                .build();

        PersistenceEntropyResult result = calculator.compute(graph);

        assertTrue(result.value().isEmpty());
    }

    @Test
    void mixOfFlaggedAndUnflaggedProducesTheExpectedRatio() {
        Graph.Builder builder = Graph.builder()
                .addNode(node("service", NodeType.COMPONENT, Role.APPLICATION))
                .addNode(node("repoA", NodeType.COMPONENT, Role.PERSISTENCE))
                .addNode(node("repoB", NodeType.COMPONENT, Role.PERSISTENCE))
                .addNode(node("repoC", NodeType.COMPONENT, Role.PERSISTENCE));
        builder.addEdge(NodeRef.resolved(NodeId.of("service")), NodeRef.resolved(NodeId.of("repoA")), RelationType.CALL,
                List.of(Evidence.of("java", "iterated call", ExtractionFidelity.L2_SYMBOL_RESOLVED, ExecutionContext.ITERATED)));
        builder.addEdge(NodeRef.resolved(NodeId.of("service")), NodeRef.resolved(NodeId.of("repoB")), RelationType.CALL,
                List.of(Evidence.of("java", "single call", ExtractionFidelity.L2_SYMBOL_RESOLVED, ExecutionContext.SINGLE)));
        builder.addEdge(NodeRef.resolved(NodeId.of("service")), NodeRef.resolved(NodeId.of("repoC")), RelationType.CALL,
                List.of(Evidence.of("java", "unknown context call", ExtractionFidelity.L1_SYNTAX)));

        PersistenceEntropyResult result = calculator.compute(builder.build());

        assertEquals(3, result.relevantEdges().size());
        assertEquals(1, result.flaggedEdges().size());
        assertEquals(OptionalDouble.of(1.0 / 3.0), result.value());
    }

    @Test
    void weightedValueCountsMultipleIteratedEvidenceItemsOnTheSameEdge() {
        // AERF v0.4.1 patch Amendment 3: an edge backed by two independently
        // observed iterated call sites weighs 2 in the numerator, not 1.
        Graph.Builder builder = Graph.builder()
                .addNode(node("service", NodeType.COMPONENT, Role.APPLICATION))
                .addNode(node("repoA", NodeType.COMPONENT, Role.PERSISTENCE))
                .addNode(node("repoB", NodeType.COMPONENT, Role.PERSISTENCE))
                .addNode(node("repoC", NodeType.COMPONENT, Role.PERSISTENCE));
        builder.addEdge(NodeRef.resolved(NodeId.of("service")), NodeRef.resolved(NodeId.of("repoA")), RelationType.CALL,
                List.of(
                        Evidence.of("java", "iterated call site 1", ExtractionFidelity.L2_SYMBOL_RESOLVED, ExecutionContext.ITERATED),
                        Evidence.of("java", "iterated call site 2", ExtractionFidelity.L2_SYMBOL_RESOLVED, ExecutionContext.ITERATED)));
        builder.addEdge(NodeRef.resolved(NodeId.of("service")), NodeRef.resolved(NodeId.of("repoB")), RelationType.CALL,
                List.of(Evidence.of("java", "single call", ExtractionFidelity.L2_SYMBOL_RESOLVED, ExecutionContext.SINGLE)));
        builder.addEdge(NodeRef.resolved(NodeId.of("service")), NodeRef.resolved(NodeId.of("repoC")), RelationType.CALL,
                List.of(Evidence.of("java", "one iterated call site", ExtractionFidelity.L2_SYMBOL_RESOLVED, ExecutionContext.ITERATED)));

        PersistenceEntropyResult result = calculator.compute(builder.build());

        assertEquals(3, result.relevantEdges().size());
        assertEquals(2, result.flaggedEdges().size(), "repoA and repoC are flagged; repoB is not");
        assertEquals(OptionalDouble.of(2.0 / 3.0), result.value(), "plain ratio counts repoA once, not twice");
        assertEquals(OptionalDouble.of(3.0 / 3.0), result.weightedValue(), "weighted ratio counts repoA's 2 evidence items plus repoC's 1");
    }

    @Test
    void weightedValueEqualsPlainValueWhenEveryFlaggedEdgeHasExactlyOneIteratedItem() {
        Graph graph = Graph.builder()
                .addNode(node("service", NodeType.COMPONENT, Role.APPLICATION))
                .addNode(node("repo", NodeType.COMPONENT, Role.PERSISTENCE))
                .addEdge(NodeRef.resolved(NodeId.of("service")), NodeRef.resolved(NodeId.of("repo")), RelationType.CALL,
                        List.of(Evidence.of("java", "iterated call", ExtractionFidelity.L2_SYMBOL_RESOLVED, ExecutionContext.ITERATED)))
                .build();

        PersistenceEntropyResult result = calculator.compute(graph);

        assertEquals(result.value(), result.weightedValue());
    }

    @Test
    void weightedValueIsNotBoundedToOneUnlikeThePlainRatio() {
        // AERF v0.4 section 4's [0,1] normalization applies to value() -
        // the plain "flagged / relevant" count ratio every other entropy
        // dimension also uses. weightedValue() (Amendment 3's distinct,
        // separately-reported "evidence-weighted" score from Appendix B)
        // is explicitly NOT that dimension: its own javadoc says it is
        // "strictly greater [than value()] whenever a flagged edge is
        // backed by more than one independently observed iterated call
        // site." This test proves that headroom is real, not merely
        // theoretical - a single relevant edge backed by three iterated
        // evidence items weighs 3, giving 3.0, not something clamped to 1.
        Graph graph = Graph.builder()
                .addNode(node("service", NodeType.COMPONENT, Role.APPLICATION))
                .addNode(node("repo", NodeType.COMPONENT, Role.PERSISTENCE))
                .addEdge(NodeRef.resolved(NodeId.of("service")), NodeRef.resolved(NodeId.of("repo")), RelationType.CALL,
                        List.of(
                                Evidence.of("java", "iterated call site 1", ExtractionFidelity.L2_SYMBOL_RESOLVED, ExecutionContext.ITERATED),
                                Evidence.of("java", "iterated call site 2", ExtractionFidelity.L2_SYMBOL_RESOLVED, ExecutionContext.ITERATED),
                                Evidence.of("java", "iterated call site 3", ExtractionFidelity.L2_SYMBOL_RESOLVED, ExecutionContext.ITERATED)))
                .build();

        PersistenceEntropyResult result = calculator.compute(graph);

        assertEquals(OptionalDouble.of(1.0), result.value());
        assertEquals(OptionalDouble.of(3.0), result.weightedValue());
    }

    @Test
    void theFixtureGraphsExistingCallsAreNotFlaggedSinceNoneClaimIteration() {
        Graph graph = CanonicalSampleGraphs.layeredOrderSlice();

        PersistenceEntropyResult result = calculator.compute(graph);

        assertEquals(2, result.relevantEdges().size(), "service->repository and controller->repository are both CALLs to a Persistence node");
        assertTrue(result.flaggedEdges().isEmpty());
        assertEquals(OptionalDouble.of(0.0), result.value());
    }

    @Test
    void resultIsDeterministicAcrossRepeatedRuns() {
        Graph graph = CanonicalSampleGraphs.layeredOrderSlice();

        assertEquals(calculator.compute(graph), calculator.compute(graph));
    }

    // ------------------------------------------------------------------
    // OQ-08 (post-v0.4.1 backlog): the source side of a persistence
    // context is deliberately NOT constrained. Until increment 22 that
    // was true only by omission - every test above uses an APPLICATION or
    // PRESENTATION source, so the behaviour on the shapes real codebases
    // actually produce was accidental. The tests below pin it against
    // both real shapes AERF has now observed. See
    // docs/increment-22-persistence-source-scope-decision.md.
    // ------------------------------------------------------------------

    private static Evidence iterated(String description) {
        return Evidence.of("java", description, ExtractionFidelity.L2_SYMBOL_RESOLVED, ExecutionContext.ITERATED);
    }

    private static Evidence single(String description) {
        return Evidence.of("java", description, ExtractionFidelity.L2_SYMBOL_RESOLVED, ExecutionContext.SINGLE);
    }

    @Test
    void persistenceToPersistenceIteratedCallIsFlagged() {
        // The legacy spring-framework-petclinic shape, and the only true
        // positive AERF has ever produced on real code:
        // JdbcOwnerRepositoryImpl#loadOwnersPetsAndVisits calls
        // #loadPetsAndVisits inside a loop. Both endpoints are
        // PERSISTENCE/FUNCTION - an intra-repository N+1, the textbook
        // form. Any source-role restriction would discard exactly this.
        Graph graph = Graph.builder()
                .addNode(node("repo#loadOwnersPetsAndVisits", NodeType.FUNCTION, Role.PERSISTENCE))
                .addNode(node("repo#loadPetsAndVisits", NodeType.FUNCTION, Role.PERSISTENCE))
                .addEdge(NodeRef.resolved(NodeId.of("repo#loadOwnersPetsAndVisits")),
                        NodeRef.resolved(NodeId.of("repo#loadPetsAndVisits")), RelationType.CALL,
                        List.of(iterated("call inside for-each over owners")))
                .build();

        PersistenceEntropyResult result = calculator.compute(graph);

        assertEquals(1, result.relevantEdges().size());
        assertEquals(1, result.flaggedEdges().size());
        assertEquals(OptionalDouble.of(1.0), result.value());
    }

    @Test
    void persistenceToPersistenceNonIteratedCallStillCountsInTheDenominator() {
        Graph graph = Graph.builder()
                .addNode(node("repo#findById", NodeType.FUNCTION, Role.PERSISTENCE))
                .addNode(node("repo#loadPetsAndVisits", NodeType.FUNCTION, Role.PERSISTENCE))
                .addEdge(NodeRef.resolved(NodeId.of("repo#findById")),
                        NodeRef.resolved(NodeId.of("repo#loadPetsAndVisits")), RelationType.CALL,
                        List.of(single("single call, no loop")))
                .build();

        PersistenceEntropyResult result = calculator.compute(graph);

        assertEquals(1, result.relevantEdges().size(), "an intra-repository call is still a persistence context");
        assertTrue(result.flaggedEdges().isEmpty());
        assertEquals(OptionalDouble.of(0.0), result.value());
    }

    @Test
    void sourceRoleDoesNotAffectRelevanceOrValue() {
        // The anti-regression pin for OQ-08: the same iterated call to the
        // same persistence target must measure identically no matter what
        // role the caller carries. Real scans supply PRESENTATION (modern
        // petclinic, 9 of 10 relevant edges), PERSISTENCE (legacy
        // petclinic, 8 of 8) and UNKNOWN (modern petclinic, 1 of 10)
        // sources, so all three are load-bearing, not hypothetical.
        for (Role sourceRole : Role.values()) {
            Graph graph = Graph.builder()
                    .addNode(node("caller", NodeType.FUNCTION, sourceRole))
                    .addNode(node("repo#findById", NodeType.FUNCTION, Role.PERSISTENCE))
                    .addEdge(NodeRef.resolved(NodeId.of("caller")),
                            NodeRef.resolved(NodeId.of("repo#findById")), RelationType.CALL,
                            List.of(iterated("call inside loop")))
                    .build();

            PersistenceEntropyResult result = calculator.compute(graph);

            assertEquals(1, result.relevantEdges().size(), "source role " + sourceRole + " changed relevance");
            assertEquals(OptionalDouble.of(1.0), result.value(), "source role " + sourceRole + " changed the value");
        }
    }

    @Test
    void sourceNodeTypeDoesNotAffectRelevance() {
        // The other half of "the source side is not consulted": OQ-08 also
        // asked whether the source must be "something that plausibly
        // iterates". Node type is the only structural property that could
        // stand in for that, and it is deliberately not consulted either -
        // the ITERATED signal lives on the edge's own provenance, which is
        // strictly more precise than any property of the calling node.
        for (NodeType sourceType : List.of(NodeType.FUNCTION, NodeType.COMPONENT, NodeType.SCRIPT)) {
            Graph graph = Graph.builder()
                    .addNode(node("caller", sourceType, Role.APPLICATION))
                    .addNode(node("repo#findById", NodeType.FUNCTION, Role.PERSISTENCE))
                    .addEdge(NodeRef.resolved(NodeId.of("caller")),
                            NodeRef.resolved(NodeId.of("repo#findById")), RelationType.CALL,
                            List.of(iterated("call inside loop")))
                    .build();

            assertEquals(1, calculator.compute(graph).relevantEdges().size(),
                    "source type " + sourceType + " changed relevance");
        }
    }

    @Test
    void presentationSourcedRepositoryCallsAreRelevantButUnflaggedWithoutIteration() {
        // The modern spring-petclinic shape, re-scanned on current code in
        // increment 22: 10 controller-to-repository calls, none iterated,
        // giving a defined 0.0 rather than an undefined value. This is the
        // cross-layer counterpart to the intra-repository case above, and
        // it is what a source-role allowlist would have kept while
        // discarding the one genuine finding.
        Graph.Builder builder = Graph.builder()
                .addNode(node("OwnerController#showOwner", NodeType.FUNCTION, Role.PRESENTATION))
                .addNode(node("PetController#populatePetTypes", NodeType.FUNCTION, Role.PRESENTATION))
                .addNode(node("OwnerRepository#findById", NodeType.FUNCTION, Role.PERSISTENCE))
                .addNode(node("PetTypeRepository#findPetTypes", NodeType.FUNCTION, Role.PERSISTENCE));
        builder.addEdge(NodeRef.resolved(NodeId.of("OwnerController#showOwner")),
                NodeRef.resolved(NodeId.of("OwnerRepository#findById")), RelationType.CALL,
                List.of(single("single call from a request handler")));
        builder.addEdge(NodeRef.resolved(NodeId.of("PetController#populatePetTypes")),
                NodeRef.resolved(NodeId.of("PetTypeRepository#findPetTypes")), RelationType.CALL,
                List.of(single("single call from a model-attribute method")));

        PersistenceEntropyResult result = calculator.compute(builder.build());

        assertEquals(2, result.relevantEdges().size());
        assertTrue(result.flaggedEdges().isEmpty());
        assertEquals(OptionalDouble.of(0.0), result.value(),
                "defined zero, not undefined: the contexts exist and none of them iterates");
    }

    @Test
    void legacyPetclinicShapedFixtureYieldsOneOverEight() {
        // An exact-value mirror of the committed legacy scan
        // (scripts/push-scan/sample-reports/spring-framework-petclinic.json):
        // 8 relevant persistence contexts, all PERSISTENCE-to-PERSISTENCE,
        // exactly one of them iterated -> 0.125. If a future change to the
        // heuristic's scope moves this number, it moves AERF's only real
        // N+1 measurement with it.
        Graph.Builder builder = Graph.builder()
                .addNode(node("repo#findById", NodeType.FUNCTION, Role.PERSISTENCE))
                .addNode(node("repo#findByLastName", NodeType.FUNCTION, Role.PERSISTENCE))
                .addNode(node("repo#loadOwnersPetsAndVisits", NodeType.FUNCTION, Role.PERSISTENCE))
                .addNode(node("repo#loadPetsAndVisits", NodeType.FUNCTION, Role.PERSISTENCE))
                .addNode(node("repo#getPetTypes", NodeType.FUNCTION, Role.PERSISTENCE))
                .addNode(node("petRepo#save", NodeType.FUNCTION, Role.PERSISTENCE))
                .addNode(node("petRepo#createPetParameterSource", NodeType.FUNCTION, Role.PERSISTENCE))
                .addNode(node("visitRepo#save", NodeType.FUNCTION, Role.PERSISTENCE))
                .addNode(node("visitRepo#createVisitParameterSource", NodeType.FUNCTION, Role.PERSISTENCE));
        call(builder, "repo#findById", "repo#loadPetsAndVisits", single("1"));
        call(builder, "repo#findByLastName", "repo#loadOwnersPetsAndVisits", single("2"));
        call(builder, "repo#loadOwnersPetsAndVisits", "repo#loadPetsAndVisits", iterated("3 - the N+1"));
        call(builder, "repo#loadOwnersPetsAndVisits", "repo#loadPetsAndVisits", single("4 - duplicate edge"));
        call(builder, "repo#loadPetsAndVisits", "repo#getPetTypes", single("5"));
        call(builder, "petRepo#save", "petRepo#createPetParameterSource", single("6"));
        call(builder, "petRepo#save", "petRepo#createPetParameterSource", single("7 - duplicate edge"));
        call(builder, "visitRepo#save", "visitRepo#createVisitParameterSource", single("8"));

        PersistenceEntropyResult result = calculator.compute(builder.build());

        assertEquals(8, result.relevantEdges().size());
        assertEquals(1, result.flaggedEdges().size());
        assertEquals(OptionalDouble.of(0.125), result.value());
        assertEquals(OptionalDouble.of(0.125), result.weightedValue(),
                "one flagged edge backed by exactly one ITERATED item weighs the same as the plain ratio");
    }

    private static void call(Graph.Builder builder, String from, String to, Evidence evidence) {
        builder.addEdge(NodeRef.resolved(NodeId.of(from)), NodeRef.resolved(NodeId.of(to)),
                RelationType.CALL, List.of(evidence));
    }
}
