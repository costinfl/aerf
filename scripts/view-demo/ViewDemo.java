package org.aerf.pipeline;

import org.aerf.analysis.calibration.*;
import org.aerf.analysis.governance.*;
import org.aerf.analysis.metrics.layer.LayerPolicy;
import org.aerf.analysis.view.*;
import org.aerf.model.Edge;
import org.aerf.model.NodeRef;
import org.aerf.model.Role;
import java.nio.file.Path;
import java.util.*;

/**
 * Increment 31 real-repo demonstration: the unified governance-facing view
 * over spring-petclinic, answering all five commissioned questions at once
 * with every part still separately readable.
 */
public class ViewDemo {
    private static final String PKG = "org.springframework.samples.petclinic.";
    private static final String SUBJECT = "spring-petclinic";

    public static void main(String[] a) throws Exception {
        List<Path> roots = List.of(Path.of(a[0]));
        List<Path> cp = Arrays.stream(a).skip(1).map(Path::of).toList();
        PipelineConfig base = Main.illustrativeConfig(roots, cp);
        GovernancePolicy g = base.governance();

        // Increment 26's declaration, so layer findings have a named judge.
        LayerPolicy strict = g.layerPolicy();
        Subsystems subsystems = Subsystems.of(List.of(
                Subsystem.withLayerPolicy("owner", PKG + "owner", strict),
                Subsystem.of("vet", PKG + "vet")));

        // One pass to find a real finding to excuse.
        PipelineReport probe = Pipeline.run(new PipelineConfig(base.extraction(), base.detection(),
                withEverything(g, subsystems, ApprovedExceptions.none())));
        Edge toExcuse = probe.layerEntropy().violatingEdges().get(0);

        PipelineReport report = Pipeline.run(new PipelineConfig(base.extraction(), base.detection(),
                withEverything(g, subsystems, ApprovedExceptions.of(List.of(new ApprovedException(
                        targetOf(toExcuse), "accepted for the legacy vet screens", "alice"))))));

        EntropySnapshot current = report.toEntropySnapshot(SUBJECT);
        Map<String, OptionalDouble> prior = new LinkedHashMap<>(current.dimensionValues());
        prior.put("layer", OptionalDouble.of(0.40));
        prior.put("persistence", OptionalDouble.of(0.10));
        EntropySnapshot baseline = new EntropySnapshot(SUBJECT, prior, current.governanceFingerprint());

        print("WITH A BASELINE", report.toGovernanceView(SUBJECT, baseline));
        print("WITHOUT A BASELINE", report.toGovernanceView(SUBJECT));
    }

    private static void print(String title, GovernanceView v) {
        System.out.println("=== " + title + " ===");

        System.out.println("1. what architectural condition was observed");
        for (DimensionObservation o : v.observed()) {
            System.out.printf("   %-12s %-28s %d/%d  confidence=%s%n",
                    o.dimension(), o.value(), o.findingCount(), o.relevantCount(), o.confidence());
        }
        System.out.println("   totalEntropy=" + v.totalEntropy() + "  maturity=" + v.maturity()
                + " " + v.maturityLevel().map(Enum::name).orElse("undefined"));

        System.out.println("2. what changed relative to baseline");
        v.comparison().ifPresentOrElse(
                c -> c.drift().forEach((d, delta) -> System.out.printf("   %-12s %.4f -> %.4f  delta=%+.4f%n",
                        d, delta.baselineValue(), delta.currentValue(), delta.delta())),
                () -> System.out.println("   (not answered)"));

        System.out.println("3. what governance constraints were violated");
        for (ViolatedConstraint c : v.violatedConstraints()) {
            System.out.printf("   %-32s I_k=%d lambda=%s severity=%s  unexcused=%d/%d%n",
                    c.invariantName(), c.indicatorValue(), c.weight(), c.severity(),
                    c.unexcusedViolations().size(), c.violations().size());
        }
        System.out.println("   E_inv=" + v.invariantAggregate().value() + "  (unbounded, as section 6.1 states)");

        System.out.println("4. what risk interpretation follows");
        v.comparison().ifPresentOrElse(c -> {
            System.out.println("   R=" + c.risk().value()
                    + "  entropyTerm=" + c.risk().entropyTerm() + "  driftTerm=" + c.risk().driftTerm());
            for (DriftPenalty p : c.risk().penalties()) {
                System.out.printf("      %-12s gamma=%.1f delta=%+.4f penalty=%.4f%n",
                        p.dimension(), p.gamma(), p.delta(), p.penalty());
            }
        }, () -> System.out.println("   (not answered)"));

        System.out.println("5. what evidence supports each conclusion");
        System.out.println("   " + v.findings().size() + " finding(s), "
                + v.unexcusedFindings().size() + " unexcused");
        v.findings().stream().limit(4).forEach(f -> System.out.printf("      %-12s %-6s judged-by=%-8s %s%n",
                f.dimension(),
                f.isExcused() ? "EXCUSED" : "open",
                f.governedBy().orElse("(default)"),
                f.subject().map(ViewDemo::describe).orElse("(nothing addressable)")));

        System.out.println("   unanswered:");
        if (v.unanswered().isEmpty()) {
            System.out.println("      (none - all five questions answered)");
        }
        v.unanswered().forEach(u -> System.out.println("      - " + u));
        System.out.println();
    }

    private static String describe(ExceptionTarget t) {
        return switch (t) {
            case ExceptionTarget.OfNode n -> shorten(n.nodeId());
            case ExceptionTarget.OfEdge e -> shorten(e.sourceId()) + " -> " + shorten(e.targetId());
        };
    }

    private static String shorten(String id) {
        return id.startsWith(PKG) ? id.substring(PKG.length()) : id;
    }

    private static GovernancePolicy withEverything(
            GovernancePolicy g, Subsystems subsystems, ApprovedExceptions exceptions) {
        return new GovernancePolicy(
                g.layerPolicy(), subsystems, g.includeSelfCyclesInCycleEntropy(),
                g.calibrationProfile(), g.invariants(),
                InvariantWeights.of(List.of(
                        new WeightedInvariant("no_presentation_to_persistence", 1.0),
                        new WeightedInvariant("entropy_budget", 4.0))),
                DriftSensitivity.of(2.0, List.of(
                        new WeightedDriftDimension("layer", 1.0),
                        new WeightedDriftDimension("cycle", 0.5),
                        new WeightedDriftDimension("persistence", 1.0))),
                exceptions);
    }

    private static ExceptionTarget targetOf(Edge edge) {
        return new ExceptionTarget.OfEdge(
                ((NodeRef.Resolved) edge.source()).id().value(),
                ((NodeRef.Resolved) edge.target()).id().value(),
                edge.relation());
    }
}
