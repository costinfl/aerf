package org.aerf.report;

import org.aerf.analysis.invariant.InvariantEvaluationResult;
import org.aerf.analysis.invariant.InvariantViolation;
import org.aerf.analysis.invariant.ViolationSubject;
import org.aerf.report.json.JsonObjectBuilder;
import org.aerf.report.json.JsonValue;

/** Serializes an invariant evaluation (Increment 9, AERF v0.4 section 6) to JSON. */
public final class InvariantJson {

    private InvariantJson() {
    }

    public static JsonValue evaluationResult(InvariantEvaluationResult result) {
        return new JsonObjectBuilder()
                .put("invariantName", result.invariantName())
                .put("severity", result.severity())
                .put("holds", result.holds())
                .put("indicatorValue", result.indicatorValue())
                .put("violations", JsonSupport.array(result.violations(), InvariantJson::violation))
                .build();
    }

    private static JsonValue violation(InvariantViolation violation) {
        return new JsonObjectBuilder()
                .put("subject", subject(violation.subject()))
                .put("rationale", violation.rationale())
                .build();
    }

    private static JsonValue subject(ViolationSubject subject) {
        return switch (subject) {
            case ViolationSubject.OfNode ofNode -> new JsonObjectBuilder()
                    .put("kind", "node")
                    .put("nodeId", JsonSupport.string(ofNode.nodeId()))
                    .build();
            case ViolationSubject.OfEdge ofEdge -> new JsonObjectBuilder()
                    .put("kind", "edge")
                    .put("edge", GraphJson.edge(ofEdge.edge()))
                    .build();
            case ViolationSubject.OfGraph ignored -> new JsonObjectBuilder()
                    .put("kind", "graph")
                    .build();
        };
    }
}
