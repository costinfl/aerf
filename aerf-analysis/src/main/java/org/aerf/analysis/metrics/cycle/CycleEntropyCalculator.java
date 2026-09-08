package org.aerf.analysis.metrics.cycle;

import org.aerf.model.Edge;
import org.aerf.model.Graph;
import org.aerf.model.NodeId;
import org.aerf.model.NodeRef;
import org.aerf.model.RelationType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Computes cycle entropy, {@code E_C = nodes participating in relevant
 * SCCs / total nodes} (AERF v0.4 section 4.2).
 *
 * <p>"Relevant" SCCs are, per section 4.2: any SCC with more than one
 * node, plus (only if {@code includeSelfCycles} is enabled) a single-node
 * SCC whose node has a self-loop edge among the relevant relations —
 * "Trivial single-node SCCs are excluded unless self-cycles are
 * explicitly governed." A single-node SCC with no self-loop (the common
 * case: a node simply not part of any cycle) is never relevant.
 *
 * <p>Only edges whose relation is in {@code relevantRelations}, with
 * both endpoints {@link NodeRef.Resolved} to a node present in the
 * graph, are used to detect cycles at all — the same exclusion of
 * unresolved/unclassifiable structure used by layer entropy, applied
 * here to which edges may even form a cycle.
 */
public final class CycleEntropyCalculator {

    private final Set<RelationType> relevantRelations;
    private final boolean includeSelfCycles;

    public CycleEntropyCalculator(Set<RelationType> relevantRelations, boolean includeSelfCycles) {
        this.relevantRelations = Set.copyOf(Objects.requireNonNull(relevantRelations, "relevantRelations"));
        this.includeSelfCycles = includeSelfCycles;
    }

    /**
     * Uses {@code CALL} and {@code DEPENDS} as the cycle-relevant
     * relations, for the same reason {@code LayerEntropyCalculator} does:
     * v0.4 does not name a default set for this metric either, so the set
     * from section 6.3's worked invariant example is reused rather than
     * inventing an unrelated one.
     */
    public static CycleEntropyCalculator withCallAndDependsRelations(boolean includeSelfCycles) {
        return new CycleEntropyCalculator(EnumSet.of(RelationType.CALL, RelationType.DEPENDS), includeSelfCycles);
    }

    public CycleEntropyResult compute(Graph graph) {
        Objects.requireNonNull(graph, "graph");

        List<Set<NodeId>> allSccs = StronglyConnectedComponents.find(graph, relevantRelations);
        Set<NodeId> selfLoopNodes = includeSelfCycles ? findSelfLoopNodes(graph) : Set.of();

        List<Set<NodeId>> relevant = new ArrayList<>();
        for (Set<NodeId> scc : allSccs) {
            if (scc.size() > 1) {
                relevant.add(scc);
            } else if (includeSelfCycles && selfLoopNodes.contains(scc.iterator().next())) {
                relevant.add(scc);
            }
        }

        return new CycleEntropyResult(graph.nodes().size(), relevant);
    }

    private Set<NodeId> findSelfLoopNodes(Graph graph) {
        Set<NodeId> selfLoops = new LinkedHashSet<>();
        for (Edge edge : graph.edges()) {
            if (!relevantRelations.contains(edge.relation())) {
                continue;
            }
            if (edge.source() instanceof NodeRef.Resolved source
                    && edge.target() instanceof NodeRef.Resolved target
                    && source.id().equals(target.id())) {
                selfLoops.add(source.id());
            }
        }
        return selfLoops;
    }
}
