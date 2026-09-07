package org.aerf.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical node, {@code v = (id, type, role, attributes)} per AERF v0.4
 * section 2.2, extended with an evidence list so every node's attributes
 * and role are traceable to what was actually observed (Appendix A: nodes
 * carry "attributes / evidence").
 *
 * <p>Node identity and equality are defined solely by {@link #id()}. Two
 * {@code Node} instances with the same id are the same graph entity even
 * if their role or attributes differ; this is required so that role
 * inference (section 3.2, an iterative refinement {@code R^(n+1) =
 * F(R^(n), G)}) can replace a node's role across iterations without that
 * being treated as a different node.
 */
public final class Node {

    private final NodeId id;
    private final NodeType type;
    private final Role role;
    private final Map<String, String> attributes;
    private final List<Evidence> evidence;

    private Node(NodeId id, NodeType type, Role role, Map<String, String> attributes, List<Evidence> evidence) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.role = Objects.requireNonNull(role, "role");
        // LinkedHashMap preserves insertion order; Map.copyOf does not, which would
        // undermine deterministic iteration over a node's attributes.
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(attributes, "attributes")));
        this.evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
    }

    public static Node of(NodeId id, NodeType type, Role role, Map<String, String> attributes, List<Evidence> evidence) {
        return new Node(id, type, role, attributes, evidence);
    }

    /**
     * Convenience factory for a node whose role has not yet been inferred.
     * Role.UNKNOWN is a valid, first-class outcome (section 3.5), not an
     * error state.
     */
    public static Node withUnknownRole(NodeId id, NodeType type, Map<String, String> attributes, List<Evidence> evidence) {
        return new Node(id, type, Role.UNKNOWN, attributes, evidence);
    }

    public NodeId id() {
        return id;
    }

    public NodeType type() {
        return type;
    }

    public Role role() {
        return role;
    }

    public Map<String, String> attributes() {
        return attributes;
    }

    public List<Evidence> evidence() {
        return evidence;
    }

    /** Returns a copy of this node with a different role, e.g. produced by role inference. */
    public Node withRole(Role newRole) {
        return new Node(id, type, newRole, attributes, evidence);
    }

    /** Returns a copy of this node with an additional attribute merged in. */
    public Node withAttribute(String key, String value) {
        Map<String, String> merged = new LinkedHashMap<>(attributes);
        merged.put(key, value);
        return new Node(id, type, role, merged, evidence);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Node other)) {
            return false;
        }
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Node{id=" + id + ", type=" + type + ", role=" + role + "}";
    }
}
