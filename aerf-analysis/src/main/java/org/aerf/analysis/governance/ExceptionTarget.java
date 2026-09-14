package org.aerf.analysis.governance;

import org.aerf.model.RelationType;

import java.util.Objects;

/**
 * What, specifically, an {@link ApprovedException} admits (Increment 28,
 * OQ-09). Mirrors the node/edge vocabulary {@code ViolationSubject}
 * already establishes for invariant violations — but by <em>id</em>
 * rather than by object, because an exception has to be declarable
 * before a scan runs, when no {@code Node} or {@code Edge} exists yet.
 *
 * <p><b>Exact ids, never prefixes</b> — deliberately unlike
 * {@link Subsystem}, which selects a broad architectural region by
 * prefix. An exception is a narrow admission about one specific finding
 * somebody reviewed and signed off; a prefix would be a blanket waiver
 * covering findings nobody has seen yet, including ones that do not
 * exist at the time of approval.
 *
 * <p>There is no graph-scope variant. A {@code ViolationSubject.OfGraph}
 * names nothing addressable, and a cycle finding is a <em>set</em> of
 * nodes whose excusal would need its own semantics — see
 * {@code docs/increment-28-approved-exceptions.md}.
 */
public sealed interface ExceptionTarget {

    /** A finding about one node: a security finding, or a NODE-scope invariant violation. */
    record OfNode(String nodeId) implements ExceptionTarget {
        public OfNode {
            Objects.requireNonNull(nodeId, "nodeId");
            if (nodeId.isBlank()) {
                throw new IllegalArgumentException("nodeId must not be blank");
            }
        }
    }

    /**
     * A finding about one edge: a layer violation, a flagged persistence
     * context, or an EDGE-scope invariant violation.
     *
     * <p>All three of source, target and relation are required. AERF's
     * graph is deliberately a multigraph, so two edges may share
     * endpoints and differ only in relation; naming the relation is what
     * keeps an exception from silently covering a second finding its
     * approver never looked at.
     */
    record OfEdge(String sourceId, String targetId, RelationType relation) implements ExceptionTarget {
        public OfEdge {
            Objects.requireNonNull(sourceId, "sourceId");
            Objects.requireNonNull(targetId, "targetId");
            Objects.requireNonNull(relation, "relation");
            if (sourceId.isBlank()) {
                throw new IllegalArgumentException("sourceId must not be blank");
            }
            if (targetId.isBlank()) {
                throw new IllegalArgumentException("targetId must not be blank");
            }
        }
    }
}
