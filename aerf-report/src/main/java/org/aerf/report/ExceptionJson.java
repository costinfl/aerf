package org.aerf.report;

import org.aerf.analysis.governance.ApprovedException;
import org.aerf.analysis.governance.ExceptionLedger;
import org.aerf.analysis.governance.ExceptionTarget;
import org.aerf.analysis.governance.ExcusedFinding;
import org.aerf.report.json.JsonObjectBuilder;
import org.aerf.report.json.JsonValue;

/**
 * Serializes approved exceptions and the governance verdict they produce
 * (Increment 28, OQ-09).
 *
 * <p>Note what is <em>not</em> here: nothing removes, rewrites or annotates
 * a finding. `MetricsJson` still emits every violating edge, every flagged
 * persistence context and every security finding exactly as measured. This
 * writer adds a separate account of which of them are already accepted and
 * by whom, so a reader sees the raw measurement and the governance position
 * side by side rather than one silently standing in for the other.
 */
public final class ExceptionJson {

    private ExceptionJson() {
    }

    /**
     * The ledger. Both arrays are emitted even when empty: "nothing is
     * excused" and "no exception went stale" are findings in their own
     * right, not absences.
     */
    public static JsonValue ledger(ExceptionLedger ledger) {
        return new JsonObjectBuilder()
                .put("excused", JsonSupport.array(ledger.excused(), ExceptionJson::excusedFinding))
                .put("unmatched", JsonSupport.array(ledger.unmatched(), ExceptionJson::approvedException))
                .build();
    }

    /** One declaration, as governance wrote it. */
    public static JsonValue approvedException(ApprovedException exception) {
        return new JsonObjectBuilder()
                .put("target", target(exception.target()))
                .put("reason", exception.reason())
                .put("approvedBy", exception.approvedBy())
                .build();
    }

    private static JsonValue excusedFinding(ExcusedFinding excused) {
        return new JsonObjectBuilder()
                .put("dimension", excused.dimension())
                .put("target", target(excused.target()))
                .put("reason", excused.reason())
                .put("approvedBy", excused.approvedBy())
                .build();
    }

    private static JsonValue target(ExceptionTarget target) {
        return switch (target) {
            case ExceptionTarget.OfNode ofNode -> new JsonObjectBuilder()
                    .put("kind", "node")
                    .put("nodeId", ofNode.nodeId())
                    .build();
            case ExceptionTarget.OfEdge ofEdge -> new JsonObjectBuilder()
                    .put("kind", "edge")
                    .put("sourceId", ofEdge.sourceId())
                    .put("targetId", ofEdge.targetId())
                    .put("relation", JsonSupport.string(ofEdge.relation()))
                    .build();
        };
    }
}
