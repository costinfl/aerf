package org.aerf.analysis.invariant;

import org.aerf.model.Edge;
import org.aerf.model.NodeId;

import java.util.Objects;

/** What, specifically, violated an invariant — the evaluable instance an {@link Invariant} rejected. */
public sealed interface ViolationSubject {

    record OfNode(NodeId nodeId) implements ViolationSubject {
        public OfNode {
            Objects.requireNonNull(nodeId, "nodeId");
        }
    }

    record OfEdge(Edge edge) implements ViolationSubject {
        public OfEdge {
            Objects.requireNonNull(edge, "edge");
        }
    }

    record OfGraph() implements ViolationSubject {
    }
}
