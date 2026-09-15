package org.aerf.pipeline;

import org.aerf.analysis.calibration.DimensionDrift;
import org.aerf.analysis.calibration.Drift;
import org.aerf.analysis.calibration.EntropySnapshot;
import org.aerf.analysis.calibration.Risk;
import org.aerf.analysis.calibration.RiskAssessment;
import org.aerf.analysis.governance.ApprovedException;
import org.aerf.analysis.governance.ExceptionTarget;
import org.aerf.analysis.governance.ExcusedFinding;
import org.aerf.analysis.governance.Subsystem;
import org.aerf.analysis.invariant.InvariantEvaluationResult;
import org.aerf.analysis.invariant.InvariantViolation;
import org.aerf.analysis.invariant.ViolationSubject;
import org.aerf.analysis.metrics.security.SecurityFinding;
import org.aerf.analysis.view.BaselineComparison;
import org.aerf.analysis.view.DimensionObservation;
import org.aerf.analysis.view.GovernanceView;
import org.aerf.analysis.view.TraceableFinding;
import org.aerf.analysis.view.ViolatedConstraint;
import org.aerf.model.Edge;
import org.aerf.model.NodeRef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Composes a {@link PipelineReport} into a {@link GovernanceView}
 * (Increment 31, OQ-16).
 *
 * <p><b>Strictly downstream of measurement.</b> Every value here is read
 * from a report that already exists; nothing is recomputed and no
 * calculator is involved, so this cannot move a numerator or a
 * denominator. That is the same structural guarantee
 * {@code ApprovedExceptionEvaluator} gives for excusal, and for the same
 * reason: a presentation concern that could change a measurement would
 * not be a presentation concern.
 *
 * <p>It lives in {@code aerf-pipeline} rather than beside the view types
 * because only this module can see {@link PipelineReport}. The view types
 * themselves stay in {@code aerf-analysis} so {@code aerf-report} can
 * serialize them.
 *
 * <p><b>Two granularities, deliberately.</b> A {@link DimensionObservation}
 * reports the metric's own ratio — for cycle entropy that denominator is
 * every node and that numerator is every node in a cycle. The findings
 * list reports addressable items, and for cycle entropy one relevant SCC
 * is one finding, because §4.2 measures over sets of nodes. They are
 * different counts of different things, and collapsing them would
 * misreport one or the other.
 */
final class GovernanceViewAssembler {

    private GovernanceViewAssembler() {
    }

    static GovernanceView assemble(PipelineReport report, String subjectId,
                                   Optional<EntropySnapshot> baseline) {
        Map<ExcusalKey, ApprovedException> excusals = indexExcusals(report);

        List<TraceableFinding> findings = new ArrayList<>();
        findings.addAll(layerFindings(report, excusals));
        findings.addAll(cycleFindings(report));
        findings.addAll(persistenceFindings(report, excusals));
        findings.addAll(securityFindings(report, excusals));

        return new GovernanceView(
                subjectId,
                report.toEntropySnapshot(subjectId).governanceFingerprint(),
                observations(report),
                report.totalEntropy(),
                report.maturity(),
                report.maturityLevel(),
                report.confidence(),
                baseline.map(snapshot -> compare(report, subjectId, snapshot)),
                findings,
                constraints(report, excusals),
                report.invariantAggregate(),
                report.exceptionLedger().unmatched());
    }

    /**
     * Drift and {@code R} for a caller-supplied baseline. Both come from
     * the existing pure functions unchanged, and {@code totalEntropy} is
     * handed to {@code Risk} as the report already computed it, so the
     * view's first term and the report's own number cannot disagree.
     */
    private static BaselineComparison compare(PipelineReport report, String subjectId, EntropySnapshot baseline) {
        EntropySnapshot current = report.toEntropySnapshot(subjectId);
        Map<String, DimensionDrift> drift = Drift.compute(baseline, current);
        RiskAssessment risk = Risk.compute(report.totalEntropy(), baseline, current, drift,
                report.governance().driftSensitivity());
        return new BaselineComparison(drift, risk);
    }

    private static List<DimensionObservation> observations(PipelineReport report) {
        Map<String, OptionalDouble> confidence = report.confidenceByDimension();
        return List.of(
                new DimensionObservation("layer", report.layerEntropy().value(),
                        confidenceOf(confidence, "layer"),
                        report.layerEntropy().relevantEdges().size(),
                        report.layerEntropy().violatingEdges().size()),
                new DimensionObservation("cycle", report.cycleEntropy().value(),
                        confidenceOf(confidence, "cycle"),
                        report.cycleEntropy().totalNodeCount(),
                        report.cycleEntropy().participatingNodes().size()),
                new DimensionObservation("persistence", report.persistenceEntropy().value(),
                        confidenceOf(confidence, "persistence"),
                        report.persistenceEntropy().relevantEdges().size(),
                        report.persistenceEntropy().flaggedEdges().size()),
                new DimensionObservation("security", report.securityEntropy().value(),
                        confidenceOf(confidence, "security"),
                        report.securityEntropy().opportunities().size(),
                        report.securityEntropy().flagged().size()));
    }

    private static OptionalDouble confidenceOf(Map<String, OptionalDouble> byDimension, String dimension) {
        return byDimension.getOrDefault(dimension, OptionalDouble.empty());
    }

    /**
     * Layer findings, each attributed to the subsystem whose own matrix
     * judged it (Increment 31 resolves finding E). The selection rule is
     * {@code Subsystems.governingMatrix} — the same method
     * {@code LayerEntropyCalculator} now calls to choose the matrix it
     * applies, so the attribution reported here and the verdict that
     * produced the finding cannot come apart. Empty means the default
     * matrix judged it, which includes a node claimed by a subsystem that
     * declared no matrix of its own.
     */
    private static List<TraceableFinding> layerFindings(PipelineReport report,
                                                        Map<ExcusalKey, ApprovedException> excusals) {
        List<TraceableFinding> findings = new ArrayList<>();
        for (Edge edge : report.layerEntropy().violatingEdges()) {
            Optional<ExceptionTarget> subject = targetOf(edge);
            Optional<String> governedBy = edge.source() instanceof NodeRef.Resolved resolved
                    ? report.governance().subsystems().governingMatrix(resolved.id()).map(Subsystem::name)
                    : Optional.empty();
            findings.add(new TraceableFinding("layer", subject, governedBy, excusedBy(excusals, "layer", subject)));
        }
        return findings;
    }

    /**
     * One finding per relevant SCC. It carries no subject: §4.2's unit is
     * a <em>set</em> of nodes, so naming one member would misrepresent the
     * finding, and there is no identifier for the set itself. Finding J
     * records the same gap on the excusal side, which is why no cycle
     * finding is ever excused either. The SCC's members remain listed in
     * {@code cycleEntropy.relevantSccs}.
     */
    private static List<TraceableFinding> cycleFindings(PipelineReport report) {
        return report.cycleEntropy().relevantSccs().stream()
                .map(scc -> new TraceableFinding("cycle", Optional.empty(), Optional.empty(), Optional.empty()))
                .toList();
    }

    private static List<TraceableFinding> persistenceFindings(PipelineReport report,
                                                              Map<ExcusalKey, ApprovedException> excusals) {
        List<TraceableFinding> findings = new ArrayList<>();
        for (Edge edge : report.persistenceEntropy().flaggedEdges()) {
            Optional<ExceptionTarget> subject = targetOf(edge);
            findings.add(new TraceableFinding(
                    "persistence", subject, Optional.empty(), excusedBy(excusals, "persistence", subject)));
        }
        return findings;
    }

    private static List<TraceableFinding> securityFindings(PipelineReport report,
                                                           Map<ExcusalKey, ApprovedException> excusals) {
        List<TraceableFinding> findings = new ArrayList<>();
        for (SecurityFinding finding : report.securityEntropy().flagged()) {
            Optional<ExceptionTarget> subject =
                    Optional.of(new ExceptionTarget.OfNode(finding.nodeId().value()));
            findings.add(new TraceableFinding(
                    "security", subject, Optional.empty(), excusedBy(excusals, "security", subject)));
        }
        return findings;
    }

    /**
     * Every configured invariant, held ones included — a reader must be
     * able to tell a satisfied constraint from one nobody declared.
     * λ_k sits beside §6.1's indicator, unmultiplied: the product belongs
     * to {@code InvariantContribution}, which {@link
     * PipelineReport#invariantAggregate()} already carries.
     */
    private static List<ViolatedConstraint> constraints(PipelineReport report,
                                                        Map<ExcusalKey, ApprovedException> excusals) {
        List<ViolatedConstraint> constraints = new ArrayList<>();
        for (InvariantEvaluationResult result : report.invariantResults()) {
            String dimension = "invariant:" + result.invariantName();
            List<TraceableFinding> violations = new ArrayList<>();
            for (InvariantViolation violation : result.violations()) {
                Optional<ExceptionTarget> subject = targetOf(violation.subject());
                violations.add(new TraceableFinding(
                        dimension, subject, Optional.empty(), excusedBy(excusals, dimension, subject)));
            }
            constraints.add(new ViolatedConstraint(
                    result.invariantName(),
                    result.severity(),
                    result.indicatorValue(),
                    report.governance().invariantWeights().weightFor(result.invariantName()),
                    violations));
        }
        return constraints;
    }

    /**
     * The ledger, keyed the way it was written: dimension plus target.
     * Reading the ledger rather than re-running the match is what keeps
     * this view and the report's own {@code exceptionLedger} in agreement
     * — a second evaluation could differ from the one already published.
     *
     * <p>The key is a record rather than a joined string so that no node
     * identifier containing the separator can be made to collide with
     * another; {@code ExceptionTarget} is itself a record, so equality is
     * exact.
     */
    private record ExcusalKey(String dimension, ExceptionTarget target) {
    }

    private static Map<ExcusalKey, ApprovedException> indexExcusals(PipelineReport report) {
        Map<ExcusalKey, ApprovedException> index = new LinkedHashMap<>();
        for (ExcusedFinding excused : report.exceptionLedger().excused()) {
            index.put(new ExcusalKey(excused.dimension(), excused.target()), excused.exception());
        }
        return index;
    }

    private static Optional<ApprovedException> excusedBy(Map<ExcusalKey, ApprovedException> excusals,
                                                         String dimension,
                                                         Optional<ExceptionTarget> subject) {
        return subject.map(target -> excusals.get(new ExcusalKey(dimension, target)));
    }

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
            case ViolationSubject.OfGraph ignored -> Optional.empty();
        };
    }
}
