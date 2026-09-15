package org.aerf.pipeline;

import org.aerf.analysis.calibration.*;
import org.aerf.analysis.governance.*;
import org.aerf.analysis.metrics.layer.LayerPolicy;
import org.aerf.model.Role;
import java.nio.file.Path;
import java.util.*;

/** Increment 30 real-repo demonstration: R over two petclinic measurements. */
public class RiskDemo {
    private static final String SUBJECT = "spring-petclinic";

    public static void main(String[] a) throws Exception {
        List<Path> roots = List.of(Path.of(a[0]));
        List<Path> cp = Arrays.stream(a).skip(1).map(Path::of).toList();
        PipelineConfig base = Main.illustrativeConfig(roots, cp);

        DriftSensitivity sensitivity = DriftSensitivity.of(2.0, List.of(
                new WeightedDriftDimension("layer", 1.0),
                new WeightedDriftDimension("cycle", 0.5),
                new WeightedDriftDimension("persistence", 1.0)));

        PipelineReport current = Pipeline.run(withSensitivity(base, base.governance().layerPolicy(), sensitivity));
        EntropySnapshot currentSnapshot = current.toEntropySnapshot(SUBJECT);

        // A plausible prior measurement of the same project under the same policy.
        Map<String, OptionalDouble> priorValues = new LinkedHashMap<>(currentSnapshot.dimensionValues());
        priorValues.put("layer", OptionalDouble.of(0.40));
        priorValues.put("persistence", OptionalDouble.of(0.10));
        EntropySnapshot baseline = new EntropySnapshot(
                SUBJECT, priorValues, currentSnapshot.governanceFingerprint());

        Map<String, DimensionDrift> drift = Drift.compute(baseline, currentSnapshot);
        RiskAssessment risk = Risk.compute(current.totalEntropy(), baseline, currentSnapshot, drift, sensitivity);

        System.out.println("=== same policy ===");
        System.out.println("E_total (entropy term) = " + risk.entropyTerm());
        System.out.println("drift term (beta=2.0)  = " + risk.driftTerm());
        for (DriftPenalty p : risk.penalties()) {
            System.out.printf("   %-12s gamma=%.1f delta=%+.4f penalty=%.4f%n",
                    p.dimension(), p.gamma(), p.delta(), p.penalty());
        }
        System.out.println("R                      = " + risk.value());

        // Same code, different layering matrix.
        PipelineReport relaxed = Pipeline.run(withSensitivity(base, LayerPolicy.of(
                Set.of(Role.PRESENTATION, Role.APPLICATION, Role.DOMAIN, Role.PERSISTENCE, Role.INFRASTRUCTURE),
                Map.of(Role.PRESENTATION, Set.of(Role.PRESENTATION, Role.APPLICATION, Role.PERSISTENCE),
                       Role.APPLICATION, Set.of(Role.APPLICATION, Role.DOMAIN, Role.PERSISTENCE),
                       Role.DOMAIN, Set.of(Role.DOMAIN, Role.PERSISTENCE),
                       Role.PERSISTENCE, Set.of(Role.PERSISTENCE, Role.INFRASTRUCTURE),
                       Role.INFRASTRUCTURE, Set.of(Role.INFRASTRUCTURE))), sensitivity));
        EntropySnapshot relaxedSnapshot = relaxed.toEntropySnapshot(SUBJECT);
        RiskAssessment refused = Risk.compute(current.totalEntropy(), relaxedSnapshot, currentSnapshot,
                Drift.compute(relaxedSnapshot, currentSnapshot), sensitivity);

        System.out.println();
        System.out.println("=== policy changed between the two measurements ===");
        System.out.println("layer entropy " + relaxed.layerEntropy().value() + " -> " + current.layerEntropy().value()
                + "  (same code, different matrix)");
        System.out.println("R = " + refused.value());
        refused.undefinedBecause().forEach(r -> System.out.println("   because: " + r));
    }

    private static PipelineConfig withSensitivity(PipelineConfig base, LayerPolicy layerPolicy,
                                                  DriftSensitivity sensitivity) {
        GovernancePolicy g = base.governance();
        return new PipelineConfig(base.extraction(), base.detection(), new GovernancePolicy(
                layerPolicy, g.subsystems(), g.includeSelfCyclesInCycleEntropy(), g.calibrationProfile(),
                g.invariants(), g.invariantWeights(), sensitivity, g.approvedExceptions()));
    }
}
