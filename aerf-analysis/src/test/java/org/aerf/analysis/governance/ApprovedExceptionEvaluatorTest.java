package org.aerf.analysis.governance;

import org.aerf.analysis.invariant.InvariantEvaluationResult;
import org.aerf.analysis.invariant.InvariantViolation;
import org.aerf.analysis.invariant.ViolationSubject;
import org.aerf.analysis.metrics.cycle.CycleEntropyCalculator;
import org.aerf.analysis.metrics.layer.LayerEntropyCalculator;
import org.aerf.analysis.metrics.layer.LayerEntropyResult;
import org.aerf.analysis.metrics.layer.LayerPolicy;
import org.aerf.analysis.metrics.persistence.PersistenceEntropyCalculator;
import org.aerf.analysis.metrics.persistence.PersistenceEntropyResult;
import org.aerf.analysis.metrics.security.SecurityEntropyResult;
import org.aerf.analysis.metrics.security.SecurityFinding;
import org.aerf.model.Edge;
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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Increment 28 (OQ-09): what an approved exception does, and — more
 * importantly — what it does not. See
 * {@code docs/increment-28-approved-exceptions.md}.
 */
class ApprovedExceptionEvaluatorTest {

    private static final String CONTROLLER = "com.example.OrderController";
    private static final String REPOSITORY = "com.example.OrderRepository";

    @Test
    void anExcusedFindingRemainsInTheFindingListUnchanged() {
        // The commission's constraint, asserted directly: never silently
        // delete or mutate source evidence. The violation is still there,
        // with its provenance, after being excused.
        Graph graph = layerViolation();
        LayerEntropyResult layer = layerCalculator().compute(graph);

        ExceptionLedger ledger = evaluate(layer, empty(), noSecurity(), List.of(),
                exception(edgeTarget(RelationType.CALL), "accepted for the legacy admin screen", "alice"));

        assertEquals(1, layer.violatingEdges().size(), "the finding is untouched");
        assertEquals(1, ledger.excused().size());
        assertEquals("layer", ledger.excused().get(0).dimension());
    }

    @Test
    void excusingAFindingChangesNoMeasuredValue() {
        // The decision this increment turns on. If an exception moved
        // entropy, approving one would register as code improvement
        // against a stored baseline - the architecture would look better
        // because somebody signed a form.
        Graph graph = layerViolation();
        LayerEntropyResult layer = layerCalculator().compute(graph);
        PersistenceEntropyResult persistence = PersistenceEntropyCalculator.withCallRelation().compute(graph);

        ExceptionLedger ledger = evaluate(layer, persistence, noSecurity(), List.of(),
                exception(edgeTarget(RelationType.CALL), "accepted", "alice"));

        assertEquals(1, ledger.excused().size(), "it was excused");
        assertEquals(layerCalculator().compute(graph).value(), layer.value(),
                "and layer entropy is exactly what it was");
        assertEquals(PersistenceEntropyCalculator.withCallRelation().compute(graph).value(), persistence.value());
        assertEquals(PersistenceEntropyCalculator.withCallRelation().compute(graph).weightedValue(),
                persistence.weightedValue(),
                "including the evidence-weighted score, whose numerator counts evidence items, not edges");
        assertEquals(CycleEntropyCalculator.withCallAndDependsRelations(false).compute(graph).value(),
                CycleEntropyCalculator.withCallAndDependsRelations(false).compute(graph).value());
    }

    @Test
    void anExceptionMatchingNothingIsReportedAsUnmatched() {
        // Either the code was fixed and the exception is stale, or it
        // never described a real finding. Both are worth knowing, so it
        // is reported rather than allowed to vanish.
        ApprovedException stale = new ApprovedException(
                new ExceptionTarget.OfNode("com.example.LongDeleted"), "was a known issue", "alice");

        ExceptionLedger ledger = evaluate(layerCalculator().compute(layerViolation()), empty(), noSecurity(),
                List.of(), stale);

        assertEquals(List.of(stale), ledger.unmatched());
        assertTrue(ledger.excused().isEmpty());
    }

    @Test
    void anExceptionForOneRelationDoesNotExcuseAnotherEdgeBetweenTheSameNodes() {
        // AERF's graph is a multigraph. Naming the relation is what keeps
        // an exception from silently covering a second finding its
        // approver never looked at.
        Graph graph = layerViolation();
        LayerEntropyResult layer = layerCalculator().compute(graph);

        ExceptionLedger ledger = evaluate(layer, empty(), noSecurity(), List.of(),
                exception(edgeTarget(RelationType.DEPENDS), "wrong relation", "alice"));

        assertTrue(ledger.excused().isEmpty(),
                "the violating edge is a CALL; a DEPENDS exception must not cover it");
        assertEquals(1, ledger.unmatched().size());
    }

    @Test
    void aNodeExceptionExcusesASecurityFindingButNotAnEdgeFinding() {
        Graph graph = layerViolation();
        SecurityEntropyResult security = new SecurityEntropyResult(
                List.of(securityFinding()), List.of(securityFinding()));

        ExceptionLedger ledger = evaluate(layerCalculator().compute(graph), empty(), security, List.of(),
                exception(new ExceptionTarget.OfNode(CONTROLLER), "output is encoded downstream", "alice"));

        assertEquals(1, ledger.excused().size());
        assertEquals("security", ledger.excused().get(0).dimension(),
                "the node exception reaches the security finding, not the layer edge that shares the node");
    }

    @Test
    void aFlaggedPersistenceContextCanBeExcused() {
        // The original register question's own subject: an approved
        // "batched or otherwise justified" exception against an N+1.
        Graph graph = iteratedPersistenceCall();
        PersistenceEntropyResult persistence = PersistenceEntropyCalculator.withCallRelation().compute(graph);

        ExceptionLedger ledger = evaluate(layerCalculator().compute(graph), persistence, noSecurity(), List.of(),
                exception(edgeTarget(RelationType.CALL), "batched at the JDBC layer", "alice"));

        assertEquals(1, persistence.flaggedEdges().size(), "still flagged");
        assertTrue(ledger.excused().stream().anyMatch(e -> e.dimension().equals("persistence")),
                ledger.excused().toString());
        assertTrue(ledger.excused().stream().allMatch(e -> e.reason().equals("batched at the JDBC layer")));
    }

    @Test
    void oneExceptionExcusesEveryFindingAtItsTargetAndEachIsListedSeparately() {
        // This fixture's edge is genuinely two findings: a presentation
        // node calling a persistence node (layer) and an iterated
        // persistence access (N+1). An exception names a location, so it
        // covers both - and the ledger says so explicitly rather than
        // reporting a single excusal that quietly stood for two. A reader
        // can then see that an approver who was thinking about one
        // dimension also signed off the other.
        Graph graph = iteratedPersistenceCall();

        ExceptionLedger ledger = evaluate(
                layerCalculator().compute(graph),
                PersistenceEntropyCalculator.withCallRelation().compute(graph),
                noSecurity(), List.of(),
                exception(edgeTarget(RelationType.CALL), "accepted", "alice"));

        assertEquals(List.of("layer", "persistence"),
                ledger.excused().stream().map(ExcusedFinding::dimension).toList());
        assertTrue(ledger.unmatched().isEmpty(), "and the exception counts as matched exactly once");
    }

    @Test
    void everyExcusedFindingNamesTheExceptionAndApproverThatExcusedIt() {
        ExceptionLedger ledger = evaluate(layerCalculator().compute(layerViolation()), empty(), noSecurity(),
                List.of(), exception(edgeTarget(RelationType.CALL), "accepted until Q3 rewrite", "alice"));

        ExcusedFinding excused = ledger.excused().get(0);
        assertEquals("alice", excused.approvedBy());
        assertEquals("accepted until Q3 rewrite", excused.reason());
        assertEquals(edgeTarget(RelationType.CALL), excused.target());
    }

    @Test
    void anEdgeScopeInvariantViolationCanBeExcusedAndIsKeyedByInvariantName() {
        InvariantEvaluationResult result = new InvariantEvaluationResult(
                "no_presentation_to_persistence", "critical",
                List.of(new InvariantViolation(new ViolationSubject.OfEdge(violatingEdge()), "presentation calls persistence")));

        ExceptionLedger ledger = evaluate(layerCalculator().compute(layerViolation()), empty(), noSecurity(),
                List.of(result), exception(edgeTarget(RelationType.CALL), "accepted", "alice"));

        assertTrue(ledger.excused().stream()
                        .anyMatch(e -> e.dimension().equals("invariant:no_presentation_to_persistence")),
                ledger.excused().toString());
    }

    @Test
    void aGraphScopeViolationCannotBeExcused() {
        // A ViolationSubject.OfGraph names nothing addressable, so there
        // is no target an approver could have reviewed. Deferred, and
        // pinned so it is reopened deliberately.
        InvariantEvaluationResult budget = new InvariantEvaluationResult(
                "entropy_budget", "critical",
                List.of(new InvariantViolation(new ViolationSubject.OfGraph(), "over budget")));

        ExceptionLedger ledger = evaluate(layerCalculator().compute(layerViolation()), empty(), noSecurity(),
                List.of(budget), exception(new ExceptionTarget.OfNode("anything"), "try", "alice"));

        assertTrue(ledger.excused().isEmpty());
        assertEquals(1, ledger.unmatched().size());
    }

    @Test
    void cycleFindingsAreNotReachableByAnException() {
        // A relevant SCC is a set of nodes; excusing one would have to
        // decide whether naming a member excuses the whole cycle. The
        // evaluator is not even given the cycle result, which is the
        // clearest possible statement that this is deferred.
        assertEquals(5, java.util.Arrays.stream(ApprovedExceptionEvaluator.class.getDeclaredMethods())
                        .filter(m -> m.getName().equals("evaluate"))
                        .findFirst().orElseThrow().getParameterCount(),
                "exceptions, layer, persistence, security, invariants - no cycle result");
    }

    @Test
    void declaringNoExceptionsProducesAnEmptyLedgerRatherThanScanningAnything() {
        ExceptionLedger ledger = ApprovedExceptionEvaluator.evaluate(
                ApprovedExceptions.none(), layerCalculator().compute(layerViolation()), empty(),
                noSecurity(), List.of());

        assertTrue(ledger.isEmpty());
    }

    private static ExceptionLedger evaluate(LayerEntropyResult layer, PersistenceEntropyResult persistence,
                                            SecurityEntropyResult security,
                                            List<InvariantEvaluationResult> invariants,
                                            ApprovedException... declared) {
        return ApprovedExceptionEvaluator.evaluate(
                ApprovedExceptions.of(List.of(declared)), layer, persistence, security, invariants);
    }

    private static ApprovedException exception(ExceptionTarget target, String reason, String approver) {
        return new ApprovedException(target, reason, approver);
    }

    private static ExceptionTarget edgeTarget(RelationType relation) {
        return new ExceptionTarget.OfEdge(CONTROLLER, REPOSITORY, relation);
    }

    private static LayerEntropyCalculator layerCalculator() {
        return LayerEntropyCalculator.withCallAndDependsRelations(LayerPolicy.of(
                Set.of(Role.PRESENTATION, Role.PERSISTENCE),
                Map.of(Role.PRESENTATION, Set.of(Role.PRESENTATION))));
    }

    private static Graph layerViolation() {
        return Graph.builder()
                .addNode(node(CONTROLLER, Role.PRESENTATION))
                .addNode(node(REPOSITORY, Role.PERSISTENCE))
                .addEdge(violatingEdge())
                .build();
    }

    private static Graph iteratedPersistenceCall() {
        return Graph.builder()
                .addNode(node(CONTROLLER, Role.PRESENTATION))
                .addNode(node(REPOSITORY, Role.PERSISTENCE))
                .addEdge(Edge.of(NodeRef.resolved(NodeId.of(CONTROLLER)), NodeRef.resolved(NodeId.of(REPOSITORY)),
                        RelationType.CALL, List.of(
                                Evidence.builder("test", "call inside a loop",
                                                ExtractionFidelity.L2_SYMBOL_RESOLVED)
                                        .executionContext(ExecutionContext.ITERATED)
                                        .build())))
                .build();
    }

    private static Edge violatingEdge() {
        return Edge.of(NodeRef.resolved(NodeId.of(CONTROLLER)), NodeRef.resolved(NodeId.of(REPOSITORY)),
                RelationType.CALL, List.of());
    }

    private static SecurityEntropyResult noSecurity() {
        return new SecurityEntropyResult(List.of(), List.of());
    }

    private static SecurityFinding securityFinding() {
        return new SecurityFinding(NodeId.of(CONTROLLER), "unencoded-output", "xss", true, "no encoding observed");
    }

    private static PersistenceEntropyResult empty() {
        return new PersistenceEntropyResult(List.of(), List.of());
    }

    private static Node node(String id, Role role) {
        return Node.of(NodeId.of(id), NodeType.COMPONENT, role, Map.of(), List.of());
    }
}
