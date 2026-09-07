package org.aerf.model;

import java.util.Objects;

/**
 * Canonical node identity. AERF v0.4 section 2.2 defines a node as
 * {@code v = (id, type, role, attributes)}; this type is that {@code id}.
 *
 * <p>An id is an opaque, stable, technology-derived string (e.g. a fully
 * qualified class name, a Maven module coordinate). AERF does not define
 * how adapters derive it; that is an extraction-level concern.
 */
public final class NodeId {

    private final String value;

    private NodeId(String value) {
        this.value = Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("NodeId value must not be blank");
        }
    }

    public static NodeId of(String value) {
        return new NodeId(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NodeId other)) {
            return false;
        }
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
