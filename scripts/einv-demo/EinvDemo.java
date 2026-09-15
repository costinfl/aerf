package org.aerf.pipeline;

import org.aerf.analysis.calibration.InvariantContribution;
import org.aerf.analysis.governance.GovernancePolicy;
import org.aerf.analysis.governance.InvariantWeights;
import org.aerf.analysis.governance.WeightedInvariant;
import org.aerf.analysis.invariant.Invariant;
import java.nio.file.Path;
import java.util.*;

/** Increment 29 real-repo demonstration: E_inv over petclinic's own invariants. */
public class EinvDemo {
    public static void main(String[] a) throws Exception {
        List<Path> roots = List.of(Path.of(a[0]));
        List<Path> cp = Arrays.stream(a).skip(1).map(Path::of).toList();
        PipelineConfig base = Main.illustrativeConfig(roots, cp);
        GovernancePolicy g = base.governance();

        List<WeightedInvariant> weights = new ArrayList<>();
        double w = 1.0;
        for (Invariant inv : g.invariants()) {
            weights.add(new WeightedInvariant(inv.name(), w));
            w += 3.0;
        }
        System.out.println("DECLARED " + weights);

        PipelineReport report = Pipeline.run(new PipelineConfig(base.extraction(), base.detection(),
                new GovernancePolicy(g.layerPolicy(), g.subsystems(), g.includeSelfCyclesInCycleEntropy(),
                        g.calibrationProfile(), g.invariants(), InvariantWeights.of(weights),
                        g.approvedExceptions())));

        System.out.println("totalEntropy=" + report.totalEntropy() + "  (unchanged by weighting)");
        System.out.println("E_inv=" + report.invariantAggregate().value());
        for (InvariantContribution c : report.invariantAggregate().contributions()) {
            System.out.println("  term  " + c.invariantName() + "  lambda=" + c.weight()
                    + "  I_k=" + c.indicatorValue() + "  contribution=" + c.contribution());
        }
        System.out.println("unevaluated=" + report.invariantAggregate().unevaluatedWeightedInvariants());
        System.out.println("skippedInvariants=" + report.skippedInvariants());
    }
}
