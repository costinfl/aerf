package org.aerf.report;

import org.aerf.model.Edge;
import org.aerf.model.Evidence;
import org.aerf.model.Graph;
import org.aerf.model.Node;
import org.aerf.model.NodeRef;
import org.aerf.report.json.JsonObjectBuilder;
import org.aerf.report.json.JsonValue;

import java.util.List;
import java.util.Map;

/**
 * Serializes the canonical graph itself — nodes, edges, evidence, and
 * provenance — to JSON. This is "JSON evidence" in the most literal
 * sense of AERF v0.4 section 11's MVP item: the raw, source-traceable
 * facts the rest of the pipeline is built on, independent of any
 * particular metric or invariant.
 */
public final class GraphJson {

    private GraphJson() {
    }

    public static JsonValue graph(Graph graph) {
        return new JsonObjectBuilder()
                .put("nodes", JsonSupport.array(List.copyOf(graph.nodes()), GraphJson::node))
                .put("edges", edges(graph.edges()))
                .build();
    }

    public static JsonValue node(Node node) {
        return new JsonObjectBuilder()
                .put("id", JsonSupport.string(node.id()))
                .put("type", JsonSupport.string(node.type()))
                .put("role", JsonSupport.string(node.role()))
                .put("attributes", attributes(node.attributes()))
                .put("evidence", JsonSupport.array(node.evidence(), GraphJson::evidence))
                .build();
    }

    public static JsonValue edges(List<Edge> edges) {
        return JsonSupport.array(edges, GraphJson::edge);
    }

    public static JsonValue edge(Edge edge) {
        return new JsonObjectBuilder()
                .put("source", nodeRef(edge.source()))
                .put("target", nodeRef(edge.target()))
                .put("relation", JsonSupport.string(edge.relation()))
                .put("provenance", JsonSupport.array(edge.provenance(), GraphJson::evidence))
                .build();
    }

    public static JsonValue nodeRef(NodeRef ref) {
        return switch (ref) {
            case NodeRef.Resolved resolved -> new JsonObjectBuilder()
                    .put("resolved", true)
                    .put("id", JsonSupport.string(resolved.id()))
                    .build();
            case NodeRef.Unresolved unresolved -> new JsonObjectBuilder()
                    .put("resolved", false)
                    .put("description", unresolved.description())
                    .build();
        };
    }

    /**
     * <b>{@code attributes} is not decoration</b> (finding R, fixed after
     * Increment 31). Until then this method emitted everything about an
     * evidence item <em>except</em> its attribute map — while
     * {@code DefaultSeedRules} matches on
     * {@code attributes().get("annotation")}, and on a real
     * spring-petclinic scan that attribute is what assigns <b>43 of 118</b>
     * nodes their {@code PRESENTATION} role.
     *
     * <p>So every report this project had produced showed a role whose
     * deciding evidence was invisible in the report: a reader could see
     * {@code "@Controller annotation observed"} as free text but not the
     * resolved fully-qualified name the rule actually matched, which is
     * the whole difference between a real annotation and something that
     * merely looks like one by simple name. §3.5 requires evidence to stay
     * traceable, and it was not.
     *
     * <p>The map is emitted the same way {@link #node(Node)} emits a
     * node's attributes: an object built by iterating the map's own
     * insertion order, which {@code Evidence} preserves with a
     * {@code LinkedHashMap} precisely so serialized output is
     * deterministic across runs (§14). An evidence item with no attributes
     * serializes as {@code {}} rather than omitting the key, matching how
     * node attributes have always behaved.
     */
    public static JsonValue evidence(Evidence evidence) {
        return new JsonObjectBuilder()
                .put("sourceAdapter", evidence.sourceAdapter())
                .put("description", evidence.description())
                .putNullableString("location", evidence.location().orElse(null))
                .put("fidelity", JsonSupport.string(evidence.fidelity()))
                .put("executionContext", JsonSupport.string(evidence.executionContext()))
                .put("attributes", attributes(evidence.attributes()))
                .build();
    }

    /** One renderer for both node and evidence attribute maps, so the two cannot diverge. */
    private static JsonValue attributes(Map<String, String> attributes) {
        JsonObjectBuilder builder = new JsonObjectBuilder();
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            builder.put(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }
}
