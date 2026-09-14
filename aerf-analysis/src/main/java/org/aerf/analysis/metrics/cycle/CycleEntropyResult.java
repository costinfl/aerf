package org.aerf.analysis.metrics.cycle;

import org.aerf.model.NodeId;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * The result of computing cycle entropy (AERF v0.4 section 4.2) over one
 * graph. {@code relevantSccs} holds the actual participating strongly
 * connected components, not just a count, so a finding stays traceable
 * to the specific nodes forming each cycle.
 *
 * <p>{@code bySubsystem} carries the same measurement scoped to each
 * declared subsystem (Increment 27, OQ-06), in declaration order, and is
 * empty when no subsystem was declared. It sits <em>beside</em>
 * {@link #value()}, which always remains the graph-wide figure section
 * 4.2 defines — scoping adds a reading, it never replaces one.
 */
public record CycleEntropyResult(
        int totalNodeCount,
        List<Set<NodeId>> relevantSccs,
        List<SubsystemCycleEntropy> bySubsystem) {

    public CycleEntropyResult(int totalNodeCount, List<Set<NodeId>> relevantSccs) {
        this(totalNodeCount, relevantSccs, List.of());
    }

    public CycleEntropyResult {
        relevantSccs = relevantSccs.stream().map(Set::copyOf).toList();
        bySubsystem = List.copyOf(java.util.Objects.requireNonNull(bySubsystem, "bySubsystem"));
    }

    /** The union of every relevant SCC's nodes. */
    public Set<NodeId> participatingNodes() {
        Set<NodeId> union = new LinkedHashSet<>();
        for (Set<NodeId> scc : relevantSccs) {
            union.addAll(scc);
        }
        return Collections.unmodifiableSet(union);
    }

    /**
     * {@code nodes participating in relevant SCCs / total nodes}, per
     * section 4.2. Empty when the graph has no nodes at all: the
     * dimension is undefined, not zero, for the same reason as layer
     * entropy (see {@code LayerEntropyResult}).
     */
    public OptionalDouble value() {
        if (totalNodeCount == 0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of((double) participatingNodes().size() / totalNodeCount);
    }
}
