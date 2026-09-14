package org.aerf.analysis.governance;

import org.aerf.analysis.invariant.InvariantEvaluationResult;
import org.aerf.analysis.invariant.InvariantViolation;
import org.aerf.analysis.invariant.ViolationSubject;
import org.aerf.analysis.metrics.layer.LayerEntropyResult;
import org.aerf.analysis.metrics.persistence.PersistenceEntropyResult;
import org.aerf.analysis.metrics.security.SecurityEntropyResult;
import org.aerf.analysis.metrics.security.SecurityFinding;
import org.aerf.model.Edge;
import org.aerf.model.NodeRef;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Matches an organization's declared {@link ApprovedExceptions} against
 * the findings one run actually produced, yielding an
 * {@link ExceptionLedger} (Increment 28, OQ-09).
 *
 * <p><b>This runs strictly downstream of every calculator and mutates
 * nothing.</b> It is handed finished results and returns a new value;
 * no metric result is filtered, rewritten, or given an "excused" bucket,
 * and no calculator knows this class exists. That is deliberate, for
 * three reasons:
 *
 * <ul>
 *   <li>Increment 6 warned that "building a one-off exception mechanism
 *       just for this metric would be exactly the kind of premature,
 *       metric-specific abstraction the project's philosophy warns
 *       against", and {@code PersistenceEntropyCalculator}'s own javadoc
 *       says the representation "belongs naturally to the
 *       invariant/exception model (section 6), not to this metric".
 *   <li>One mechanism serves every finding type instead of five.
 *   <li>It makes measurement-neutrality <em>structural</em> rather than
 *       argued: no calculator changes, so no measured value can move.
 * </ul>
 *
 * <p><b>One exception can excuse findings in more than one dimension.</b>
 * An exception names a <em>location</em>, and one edge can genuinely be
 * two findings at once — a presentation node calling a persistence node
 * is a layer violation, and if that call sits in a loop it is also a
 * flagged N+1. Rather than let one excusal quietly stand for both, the
 * ledger records each separately, so a reader can see that an approver
 * reasoning about one dimension also signed off the other. The exception
 * itself still counts as matched exactly once.
 *
 * <p><b>What can be excused.</b> Any finding identifiable as a single
 * node or a single edge: layer violations, flagged persistence contexts,
 * flagged security findings, and NODE- or EDGE-scope invariant
 * violations. Two things deliberately cannot, each recorded rather than
 * quietly handled:
 *
 * <ul>
 *   <li><b>Cycle findings.</b> A relevant SCC is a <em>set</em> of nodes.
 *       Excusing one would have to answer whether naming a single member
 *       excuses the whole cycle, and what a partially-excused cycle
 *       means for the node-scoped ratio — semantics this increment does
 *       not decide.
 *   <li><b>GRAPH-scope invariant violations.</b> A
 *       {@code ViolationSubject.OfGraph} names nothing addressable.
 * </ul>
 */
public final class ApprovedExceptionEvaluator {

    private ApprovedExceptionEvaluator() {
    }

    /**
     * @param securityEntropy may be null only if a caller has no security
     *                        result at all; every other argument is required
     */
    public static ExceptionLedger evaluate(
            ApprovedExceptions exceptions,
            LayerEntropyResult layerEntropy,
            PersistenceEntropyResult persistenceEntropy,
            SecurityEntropyResult securityEntropy,
            List<InvariantEvaluationResult> invariantResults) {

        Objects.requireNonNull(exceptions, "exceptions");
        Objects.requireNonNull(layerEntropy, "layerEntropy");
        Objects.requireNonNull(persistenceEntropy, "persistenceEntropy");
        Objects.requireNonNull(securityEntropy, "securityEntropy");
        Objects.requireNonNull(invariantResults, "invariantResults");

        if (exceptions.isEmpty()) {
            return ExceptionLedger.empty();
        }

        List<ExcusedFinding> excused = new ArrayList<>();
        Set<ApprovedException> matched = new LinkedHashSet<>();

        for (Edge edge : layerEntropy.violatingEdges()) {
            record(exceptions, excused, matched, "layer", targetOf(edge));
        }
        for (Edge edge : persistenceEntropy.flaggedEdges()) {
            record(exceptions, excused, matched, "persistence", targetOf(edge));
        }
        for (SecurityFinding finding : securityEntropy.flagged()) {
            record(exceptions, excused, matched, "security",
                    Optional.of(new ExceptionTarget.OfNode(finding.nodeId().value())));
        }
        for (InvariantEvaluationResult result : invariantResults) {
            for (InvariantViolation violation : result.violations()) {
                record(exceptions, excused, matched, "invariant:" + result.invariantName(),
                        targetOf(violation.subject()));
            }
        }

        List<ApprovedException> unmatched = exceptions.declared().stream()
                .filter(declared -> !matched.contains(declared))
                .toList();

        return new ExceptionLedger(excused, unmatched);
    }

    private static void record(ApprovedExceptions exceptions, List<ExcusedFinding> excused,
                               Set<ApprovedException> matched, String dimension,
                               Optional<ExceptionTarget> target) {
        target.flatMap(exceptions::excusing).ifPresent(exception -> {
            excused.add(new ExcusedFinding(dimension, target.orElseThrow(), exception));
            matched.add(exception);
        });
    }

    /**
     * An edge is addressable only when both endpoints resolved. An
     * unresolved reference names nothing an approver could have reviewed
     * — and no finding list here can contain one anyway, since every
     * calculator requires both endpoints resolved to consider an edge at
     * all.
     */
    private static Optional<ExceptionTarget> targetOf(Edge edge) {
        if (edge.source() instanceof NodeRef.Resolved source
                && edge.target() instanceof NodeRef.Resolved target) {
            return Optional.of(new ExceptionTarget.OfEdge(
                    source.id().value(), target.id().value(), edge.relation()));
        }
        return Optional.empty();
    }

    private static Optional<ExceptionTarget> targetOf(ViolationSubject subject) {
        return switch (subject) {
            case ViolationSubject.OfNode ofNode ->
                    Optional.of(new ExceptionTarget.OfNode(ofNode.nodeId().value()));
            case ViolationSubject.OfEdge ofEdge -> targetOf(ofEdge.edge());
            // A graph-scope violation names nothing addressable.
            case ViolationSubject.OfGraph ignored -> Optional.empty();
        };
    }
}
