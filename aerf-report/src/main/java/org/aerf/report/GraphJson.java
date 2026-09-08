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
        JsonObjectBuilder attributes = new JsonObjectBuilder();
        for (Map.Entry<String, String> entry : node.attributes().entrySet()) {
            attributes.put(entry.getKey(), entry.getValue());
        }
        return new JsonObjectBuilder()
                .put("id", JsonSupport.string(node.id()))
                .put("type", JsonSupport.string(node.type()))
                .put("role", JsonSupport.string(node.role()))
                .put("attributes", attributes.build())
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

    public static JsonValue evidence(Evidence evidence) {
        return new JsonObjectBuilder()
                .put("sourceAdapter", evidence.sourceAdapter())
                .put("description", evidence.description())
                .putNullableString("location", evidence.location().orElse(null))
                .put("fidelity", JsonSupport.string(evidence.fidelity()))
                .put("executionContext", JsonSupport.string(evidence.executionContext()))
                .build();
    }
}
