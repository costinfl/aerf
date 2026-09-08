package org.aerf.extraction;

import java.util.Objects;

/**
 * A symbolic reference to a node an extractor has not yet confirmed
 * exists — what an extractor emits for the source or target of a
 * relation before it can be checked against the set of declarations the
 * extractor has (or has not) produced.
 *
 * <p>This exists because {@code Graph.Builder} requires a resolved
 * edge's node to already be present at the moment the edge is added
 * (AERF v0.4 section 8), but a real extractor typically sees a reference
 * to a type before it has parsed that type's own declaration. A
 * {@code SymbolRef} defers that check to {@link GraphAssembler}, which
 * sees every declaration before resolving any reference.
 *
 * <p>{@code key} must equal the exact string an eventual
 * {@link org.aerf.model.NodeId#value()} would carry for the same
 * declaration, for resolution to succeed — see {@link JavaNodeIds} for
 * the Java convention this project uses to derive that string
 * consistently on both the declaring and referencing sides.
 */
public record SymbolRef(String key, String description) {

    public SymbolRef {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(description, "description");
        if (key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if (description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
    }

    /** A reference whose key is itself a sufficient description. */
    public static SymbolRef of(String key) {
        return new SymbolRef(key, key);
    }

    public static SymbolRef of(String key, String description) {
        return new SymbolRef(key, description);
    }
}
