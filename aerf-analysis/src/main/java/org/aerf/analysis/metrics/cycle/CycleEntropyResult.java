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
 */
public record CycleEntropyResult(int totalNodeCount, List<Set<NodeId>> relevantSccs) {

    public CycleEntropyResult {
        relevantSccs = relevantSccs.stream().map(Set::copyOf).toList();
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
