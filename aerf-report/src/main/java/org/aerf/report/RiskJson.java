package org.aerf.report;

import org.aerf.analysis.calibration.DriftPenalty;
import org.aerf.analysis.calibration.RiskAssessment;
import org.aerf.report.json.JsonObjectBuilder;
import org.aerf.report.json.JsonValue;

/**
 * Serializes AERF v0.4 §5.3's risk model {@code R} (Increment 30, OQ-14).
 *
 * <p>Deliberately a standalone mapper, like {@link DriftJson} and for the
 * same reason: it serializes a result a caller already computed and does
 * not decide where in a larger report that result belongs. {@code R}
 * cannot be produced by the pipeline at all — it needs a baseline the
 * pipeline does not have — so it appears in no pipeline-produced JSON,
 * and composing it with entropy, drift and violations into one
 * governance-facing view is still open (open-questions-register #16).
 *
 * <p>The two terms are emitted separately beside the total, and every
 * per-dimension penalty beside the drift term, because OQ-14's commission
 * forbids an opaque single score. {@code undefinedBecause} carries every
 * reason the total is {@code null}, so an undefined risk is never a bare
 * absence.
 */
public final class RiskJson {

    private RiskJson() {
    }

    public static JsonValue assessment(RiskAssessment assessment) {
        return new JsonObjectBuilder()
                .put("value", JsonSupport.optionalDouble(assessment.value()))
                .put("entropyTerm", JsonSupport.optionalDouble(assessment.entropyTerm()))
                .put("driftTerm", JsonSupport.optionalDouble(assessment.driftTerm()))
                .put("penalties", JsonSupport.array(assessment.penalties(), RiskJson::penalty))
                .put("undefinedBecause", new JsonValue.JsonArray(
                        assessment.undefinedBecause().stream()
                                .map(reason -> (JsonValue) new JsonValue.JsonString(reason))
                                .toList()))
                .build();
    }

    /**
     * {@code delta} is the raw {@code Delta_d}, which may be negative;
     * {@code penalty} is {@code gamma * max(0, delta)}. Both are emitted
     * so a reader can see that a dimension improved <em>and</em> that its
     * improvement contributed nothing.
     */
    private static JsonValue penalty(DriftPenalty penalty) {
        return new JsonObjectBuilder()
                .put("dimension", penalty.dimension())
                .put("gamma", penalty.gamma())
                .put("delta", penalty.delta())
                .put("penalty", penalty.penalty())
                .build();
    }
}
