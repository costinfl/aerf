package org.aerf.analysis.metrics.cycle;

import org.aerf.model.NodeId;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Cycle entropy measured over one declared subsystem's own nodes
 * (Increment 27, OQ-06), reported <em>alongside</em> the graph-wide
 * figure in {@link CycleEntropyResult} rather than in place of it.
 *
 * <p><b>Both sides of the ratio are scoped.</b> Cycle entropy's
 * denominator is the whole node population, unlike layer entropy's, so
 * scoping only the numerator against a graph-wide denominator would
 * silently change what the number means. Here {@code totalNodeCount} is
 * the count of nodes <em>this subsystem claims</em>, and
 * {@code participatingNodes} are the ones among them that sit in a
 * relevant SCC.
 *
 * <p><b>A cycle spanning two subsystems belongs to neither.</b> An SCC is
 * a set of nodes and can straddle a boundary, so it is never assigned an
 * owner — each subsystem simply counts the participating nodes it claims.
 * A consequence worth stating: the subsystem numerators sum to at most
 * the graph-wide numerator, with equality exactly when every
 * participating node is claimed by some subsystem.
 *
 * <p>{@link #value()} is undefined for a subsystem that claims no node in
 * this graph, never {@code 0.0}: "this subsystem was not measurable here"
 * is a different statement from "this subsystem has no cycles", and the
 * second would be a claim nothing supports. That mirrors
 * {@link CycleEntropyResult#value()}'s own rule for an empty graph rather
 * than inventing a second one.
 */
public record SubsystemCycleEntropy(String subsystem, int totalNodeCount, Set<NodeId> participatingNodes) {

    public SubsystemCycleEntropy {
        Objects.requireNonNull(subsystem, "subsystem");
        participatingNodes = Set.copyOf(Objects.requireNonNull(participatingNodes, "participatingNodes"));
        if (subsystem.isBlank()) {
            throw new IllegalArgumentException("subsystem must not be blank");
        }
        if (totalNodeCount < 0) {
            throw new IllegalArgumentException("totalNodeCount must not be negative: " + totalNodeCount);
        }
        if (participatingNodes.size() > totalNodeCount) {
            throw new IllegalArgumentException(
                    "participatingNodes (" + participatingNodes.size() + ") cannot exceed totalNodeCount ("
                            + totalNodeCount + "): a participating node is by construction one this subsystem claims");
        }
    }

    /**
     * {@code claimed nodes participating in relevant SCCs / claimed
     * nodes}. Empty when this subsystem claims no node in this graph.
     */
    public OptionalDouble value() {
        if (totalNodeCount == 0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of((double) participatingNodes.size() / totalNodeCount);
    }
}
