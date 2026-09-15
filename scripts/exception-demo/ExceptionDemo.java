package org.aerf.pipeline;

import org.aerf.analysis.governance.*;
import org.aerf.model.Edge;
import org.aerf.model.NodeRef;
import java.nio.file.Path;
import java.util.*;

/**
 * Increment 28 real-repo demonstration: excuse the one genuine N+1 AERF
 * has ever found on real code, and show the measurement does not move.
 */
public class ExceptionDemo {
    public static void main(String[] a) throws Exception {
        List<Path> roots = List.of(Path.of(a[0]));
        List<Path> cp = Arrays.stream(a).skip(1).map(Path::of).toList();
        PipelineConfig base = Main.illustrativeConfig(roots, cp);

        PipelineReport plain = Pipeline.run(base);
        System.out.println("BEFORE  E_P=" + plain.persistenceEntropy().value()
                + " weighted=" + plain.persistenceEntropy().weightedValue()
                + " relevant=" + plain.persistenceEntropy().relevantEdges().size()
                + " flagged=" + plain.persistenceEntropy().flaggedEdges().size()
                + " total=" + plain.totalEntropy());

        if (plain.persistenceEntropy().flaggedEdges().isEmpty()) {
            System.out.println("no flagged persistence context found; nothing to excuse");
            return;
        }
        Edge nPlusOne = plain.persistenceEntropy().flaggedEdges().get(0);
        ExceptionTarget target = new ExceptionTarget.OfEdge(
                ((NodeRef.Resolved) nPlusOne.source()).id().value(),
                ((NodeRef.Resolved) nPlusOne.target()).id().value(),
                nPlusOne.relation());
        System.out.println("EXCUSING " + target);

        GovernancePolicy g = base.governance();
        PipelineReport excused = Pipeline.run(new PipelineConfig(base.extraction(), base.detection(),
                new GovernancePolicy(g.layerPolicy(), g.subsystems(), g.includeSelfCyclesInCycleEntropy(),
                        g.calibrationProfile(), g.invariants(), g.invariantWeights(),
                        ApprovedExceptions.of(List.of(new ApprovedException(target,
                                "batched at the JDBC layer; reviewed 2026-09", "alice"))))));

        System.out.println("AFTER   E_P=" + excused.persistenceEntropy().value()
                + " weighted=" + excused.persistenceEntropy().weightedValue()
                + " relevant=" + excused.persistenceEntropy().relevantEdges().size()
                + " flagged=" + excused.persistenceEntropy().flaggedEdges().size()
                + " total=" + excused.totalEntropy());
        System.out.println("FINDING STILL LISTED: "
                + excused.persistenceEntropy().flaggedEdges().equals(plain.persistenceEntropy().flaggedEdges()));
        for (ExcusedFinding f : excused.exceptionLedger().excused()) {
            System.out.println("LEDGER  " + f.dimension() + " excused by " + f.approvedBy() + " - " + f.reason());
        }
        System.out.println("UNMATCHED " + excused.exceptionLedger().unmatched().size());
    }
}
