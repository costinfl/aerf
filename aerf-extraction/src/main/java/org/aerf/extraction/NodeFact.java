package org.aerf.extraction;

import org.aerf.model.Evidence;
import org.aerf.model.NodeId;
import org.aerf.model.NodeType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A declaration an extractor observed: "this id names a node of this
 * type, with this attribute/evidence backing." No {@link org.aerf.model.Role}
 * — role inference (AERF v0.4 section 3.2) is a distinct, later pipeline
 * stage, not extraction's job.
 *
 * <p>Two {@code NodeFact}s may legitimately share an {@link #id()} — a
 * type's declaration and a separate annotation observation on it, say —
 * and {@link GraphAssembler} merges them (see its documentation for the
 * merge policy) rather than one silently overwriting the other the way
 * {@code Graph.Builder.addNode} does.
 */
public record NodeFact(NodeId id, NodeType type, Map<String, String> attributes, List<Evidence> evidence) {

    public NodeFact {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        // LinkedHashMap wrapped as unmodifiable, not Map.copyOf: see the
        // determinism note in Evidence and Node for why.
        attributes = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(attributes, "attributes")));
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
    }
}
