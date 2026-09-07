package org.aerf.model;

import java.util.Objects;

/**
 * Identity of an edge endpoint. AERF v0.4 section 8 requires adapters to
 * be "conservative" and never invent a node merely to complete a graph,
 * while section 3.5 requires that unresolved information remain
 * representable rather than fabricated or dropped.
 *
 * <p>{@link Resolved} points at an actual node already present (or to be
 * present) in the graph. {@link Unresolved} records that a relation was
 * observed but its endpoint could not be identified, together with a
 * human-readable reason, without materializing a fake node for it.
 */
public sealed interface NodeRef {

    record Resolved(NodeId id) implements NodeRef {
        public Resolved {
            Objects.requireNonNull(id, "id");
        }
    }

    record Unresolved(String description) implements NodeRef {
        public Unresolved {
            Objects.requireNonNull(description, "description");
            if (description.isBlank()) {
                throw new IllegalArgumentException("description must not be blank");
            }
        }
    }

    static NodeRef resolved(NodeId id) {
        return new Resolved(id);
    }

    static NodeRef unresolved(String description) {
        return new Unresolved(description);
    }
}
