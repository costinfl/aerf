package org.aerf.analysis.metrics.cycle;

import org.aerf.analysis.calibration.DimensionConfidence;
import org.aerf.analysis.governance.Subsystem;
import org.aerf.analysis.governance.Subsystems;
import org.aerf.model.Edge;
import org.aerf.model.Graph;
import org.aerf.model.NodeId;
import org.aerf.model.Node;
import org.aerf.model.NodeRef;
import org.aerf.model.RelationType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
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
    private final Subsystems subsystems;

    public CycleEntropyCalculator(Set<RelationType> relevantRelations, boolean includeSelfCycles) {
        this(relevantRelations, includeSelfCycles, Subsystems.none());
    }

    public CycleEntropyCalculator(Set<RelationType> relevantRelations, boolean includeSelfCycles,
                                  Subsystems subsystems) {
        this.relevantRelations = Set.copyOf(Objects.requireNonNull(relevantRelations, "relevantRelations"));
        this.includeSelfCycles = includeSelfCycles;
        this.subsystems = Objects.requireNonNull(subsystems, "subsystems");
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

    /**
     * As {@link #withCallAndDependsRelations(boolean)}, additionally
     * reporting the same measurement per declared subsystem (OQ-06). The
     * relation set, the SCC detection and the graph-wide value are
     * identical either way — a declaration adds readings, it changes none.
     */
    public static CycleEntropyCalculator withCallAndDependsRelations(
            boolean includeSelfCycles, Subsystems subsystems) {
        return new CycleEntropyCalculator(
                EnumSet.of(RelationType.CALL, RelationType.DEPENDS), includeSelfCycles, subsystems);
    }

    /**
     * How much of this dimension's own evidence was resolved (OQ-13),
     * measured over the same relation set {@link #compute} traverses.
     * Note this is edge-scoped even though cycle entropy's own ratio is
     * node-scoped: what can fail to resolve is a reference, and only
     * edges carry references.
     */
    public OptionalDouble confidence(Graph graph) {
        return DimensionConfidence.forRelations(graph, relevantRelations);
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

        CycleEntropyResult global = new CycleEntropyResult(graph.nodes().size(), relevant);
        return new CycleEntropyResult(graph.nodes().size(), relevant, scopeBySubsystem(graph, global));
    }

    /**
     * The same measurement per declared subsystem, computed strictly
     * <em>after</em> SCC detection: {@code StronglyConnectedComponents}
     * sees exactly the graph and relation set it always did, whatever is
     * declared here. Both sides of each ratio are scoped to the nodes
     * that subsystem claims, because cycle entropy's denominator is the
     * node population itself — scoping only the numerator would change
     * what the number means rather than narrow it.
     *
     * <p>A cross-subsystem SCC is not assigned an owner; each subsystem
     * counts the participating nodes it claims and no more. A node no
     * subsystem claims counts graph-wide and nowhere else.
     */
    private List<SubsystemCycleEntropy> scopeBySubsystem(Graph graph, CycleEntropyResult global) {
        if (subsystems.isEmpty()) {
            return List.of();
        }

        Map<String, Integer> claimedNodeCounts = new LinkedHashMap<>();
        Map<String, Set<NodeId>> claimedParticipants = new LinkedHashMap<>();
        for (Subsystem subsystem : subsystems.declared()) {
            claimedNodeCounts.put(subsystem.name(), 0);
            claimedParticipants.put(subsystem.name(), new LinkedHashSet<>());
        }

        Set<NodeId> participating = global.participatingNodes();
        for (Node node : graph.nodes()) {
            subsystems.governing(node.id()).ifPresent(subsystem -> {
                claimedNodeCounts.merge(subsystem.name(), 1, Integer::sum);
                if (participating.contains(node.id())) {
                    claimedParticipants.get(subsystem.name()).add(node.id());
                }
            });
        }

        return subsystems.declared().stream()
                .map(subsystem -> new SubsystemCycleEntropy(
                        subsystem.name(),
                        claimedNodeCounts.get(subsystem.name()),
                        claimedParticipants.get(subsystem.name())))
                .toList();
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
