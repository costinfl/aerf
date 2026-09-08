package org.aerf.report;

import org.aerf.analysis.metrics.cycle.CycleEntropyResult;
import org.aerf.analysis.metrics.layer.LayerEntropyResult;
import org.aerf.analysis.metrics.persistence.PersistenceEntropyResult;
import org.aerf.analysis.metrics.security.SecurityEntropyResult;
import org.aerf.analysis.metrics.security.SecurityFinding;
import org.aerf.model.NodeId;
import org.aerf.report.json.JsonObjectBuilder;
import org.aerf.report.json.JsonValue;

import java.util.List;
import java.util.Set;

/**
 * Serializes each entropy calculator's result (Increments 3, 5, 6, 7) to
 * JSON. Every result keeps the full evidence its calculator already
 * collected (relevant/violating edges, participating SCCs, security
 * findings) — this is a direct mapping, not a summary, per "measurement
 * before aggregation" (section 14): a report reader should never have to
 * take a ratio's word for it without being able to see what produced it.
 */
public final class MetricsJson {

    private MetricsJson() {
    }

    public static JsonValue layerEntropy(LayerEntropyResult result) {
        return new JsonObjectBuilder()
                .put("value", JsonSupport.optionalDouble(result.value()))
                .put("relevantEdgeCount", result.relevantEdges().size())
                .put("violatingEdgeCount", result.violatingEdges().size())
                .put("relevantEdges", GraphJson.edges(result.relevantEdges()))
                .put("violatingEdges", GraphJson.edges(result.violatingEdges()))
                .build();
    }

    public static JsonValue cycleEntropy(CycleEntropyResult result) {
        JsonValue sccs = JsonSupport.array(result.relevantSccs(), MetricsJson::nodeIdSet);
        return new JsonObjectBuilder()
                .put("value", JsonSupport.optionalDouble(result.value()))
                .put("totalNodeCount", result.totalNodeCount())
                .put("participatingNodeCount", result.participatingNodes().size())
                .put("relevantSccs", sccs)
                .build();
    }

    public static JsonValue persistenceEntropy(PersistenceEntropyResult result) {
        return new JsonObjectBuilder()
                .put("value", JsonSupport.optionalDouble(result.value()))
                .put("weightedValue", JsonSupport.optionalDouble(result.weightedValue()))
                .put("relevantEdgeCount", result.relevantEdges().size())
                .put("flaggedEdgeCount", result.flaggedEdges().size())
                .put("relevantEdges", GraphJson.edges(result.relevantEdges()))
                .put("flaggedEdges", GraphJson.edges(result.flaggedEdges()))
                .build();
    }

    public static JsonValue securityEntropy(SecurityEntropyResult result) {
        return new JsonObjectBuilder()
                .put("value", JsonSupport.optionalDouble(result.value()))
                .put("opportunityCount", result.opportunities().size())
                .put("flaggedCount", result.flagged().size())
                .put("opportunities", JsonSupport.array(result.opportunities(), MetricsJson::securityFinding))
                .put("flagged", JsonSupport.array(result.flagged(), MetricsJson::securityFinding))
                .build();
    }

    private static JsonValue securityFinding(SecurityFinding finding) {
        return new JsonObjectBuilder()
                .put("nodeId", JsonSupport.string(finding.nodeId()))
                .put("ruleName", finding.ruleName())
                .put("concern", finding.concern())
                .put("weaknessDetected", finding.weaknessDetected())
                .put("rationale", finding.rationale())
                .build();
    }

    private static JsonValue nodeIdSet(Set<NodeId> ids) {
        // NodeId has no natural order beyond its string value; sorted so
        // identical input always serializes identically regardless of the
        // originating Set's own iteration order.
        List<String> sorted = ids.stream().map(NodeId::value).sorted().toList();
        return new JsonValue.JsonArray(sorted.stream().map(id -> (JsonValue) new JsonValue.JsonString(id)).toList());
    }
}
