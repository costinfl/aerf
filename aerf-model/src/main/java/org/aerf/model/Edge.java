package org.aerf.model;

import java.util.List;
import java.util.Objects;

/**
 * Canonical edge, {@code e = (v_i, v_j, label)} per AERF v0.4 section 2.4,
 * extended with provenance as required by section 8 ("every relation
 * should carry provenance sufficient to explain its source") and Appendix
 * A, which lists {@code label, source, target, provenance} as an edge's
 * fields.
 *
 * <p>An {@code Edge} has no identity of its own beyond its fields: AERF's
 * graph is explicitly a multigraph, so two edges with the same endpoints
 * and relation but different evidence are both legitimate and distinct.
 */
public final class Edge {

    private final NodeRef source;
    private final NodeRef target;
    private final RelationType relation;
    private final List<Evidence> provenance;

    private Edge(NodeRef source, NodeRef target, RelationType relation, List<Evidence> provenance) {
        this.source = Objects.requireNonNull(source, "source");
        this.target = Objects.requireNonNull(target, "target");
        this.relation = Objects.requireNonNull(relation, "relation");
        this.provenance = List.copyOf(Objects.requireNonNull(provenance, "provenance"));
    }

    public static Edge of(NodeRef source, NodeRef target, RelationType relation, List<Evidence> provenance) {
        return new Edge(source, target, relation, provenance);
    }

    public NodeRef source() {
        return source;
    }

    public NodeRef target() {
        return target;
    }

    public RelationType relation() {
        return relation;
    }

    public List<Evidence> provenance() {
        return provenance;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Edge other)) {
            return false;
        }
        return source.equals(other.source)
                && target.equals(other.target)
                && relation == other.relation
                && provenance.equals(other.provenance);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, target, relation, provenance);
    }

    @Override
    public String toString() {
        return "Edge{" + source + " -" + relation + "-> " + target + "}";
    }
}
