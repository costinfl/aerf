package org.aerf.report;

import org.aerf.analysis.view.BaselineComparison;
import org.aerf.analysis.view.DimensionObservation;
import org.aerf.analysis.view.GovernanceView;
import org.aerf.analysis.view.TraceableFinding;
import org.aerf.analysis.view.ViolatedConstraint;
import org.aerf.report.json.JsonObjectBuilder;
import org.aerf.report.json.JsonValue;

import java.util.List;

/**
 * Serializes OQ-16's unified governance view (Increment 31).
 *
 * <p>A standalone mapper, following {@code DriftJson} and {@code RiskJson}
 * rather than the report mappers: it serializes a value a caller composed
 * and does not decide where in a larger document it belongs. There is no
 * {@code Main.toJson} key for it, because two of its five answers need a
 * baseline the pipeline does not have — the same reason {@code R} has no
 * key.
 *
 * <p>The output keys are the commission's five questions in order, so the
 * document itself carries the structure the backlog asked for rather than
 * leaving a reader to map fields back onto it.
 */
public final class GovernanceViewJson {

    private GovernanceViewJson() {
    }

    public static JsonValue view(GovernanceView view) {
        return new JsonObjectBuilder()
                .put("subjectId", view.subjectId())
                .putNullableString("governanceFingerprint", view.governanceFingerprint().orElse(null))
                .put("observed", observed(view))
                .put("changed", view.comparison().map(GovernanceViewJson::changed).orElse(JsonValue.JsonNull.INSTANCE))
                .put("violated", violated(view))
                .put("risk", view.comparison()
                        .map(comparison -> RiskJson.assessment(comparison.risk()))
                        .orElse(JsonValue.JsonNull.INSTANCE))
                .put("findings", JsonSupport.array(view.findings(), GovernanceViewJson::finding))
                .put("unanswered", strings(view.unanswered()))
                .build();
    }

    /** Question 1: what architectural condition was observed. */
    private static JsonValue observed(GovernanceView view) {
        return new JsonObjectBuilder()
                .put("dimensions", JsonSupport.array(view.observed(), GovernanceViewJson::observation))
                .put("totalEntropy", JsonSupport.optionalDouble(view.totalEntropy()))
                .put("maturity", JsonSupport.optionalDouble(view.maturity()))
                .put("maturityLevel", view.maturityLevel()
                        .map(level -> (JsonValue) new JsonValue.JsonString(level.name()))
                        .orElse(JsonValue.JsonNull.INSTANCE))
                .put("confidence", JsonSupport.optionalDouble(view.confidence()))
                .build();
    }

    private static JsonValue observation(DimensionObservation observation) {
        return new JsonObjectBuilder()
                .put("dimension", observation.dimension())
                .put("value", JsonSupport.optionalDouble(observation.value()))
                .put("confidence", JsonSupport.optionalDouble(observation.confidence()))
                .put("relevantCount", observation.relevantCount())
                .put("findingCount", observation.findingCount())
                .build();
    }

    /**
     * Question 2: what changed relative to baseline. {@code null} rather
     * than an empty object when no baseline was supplied — an empty object
     * would read as "compared, nothing moved", which is a different claim.
     */
    private static JsonValue changed(BaselineComparison comparison) {
        return new JsonObjectBuilder()
                .put("drift", DriftJson.drift(comparison.drift()))
                .build();
    }

    /** Question 3: what governance constraints were violated. */
    private static JsonValue violated(GovernanceView view) {
        return new JsonObjectBuilder()
                .put("constraints", JsonSupport.array(view.violatedConstraints(), GovernanceViewJson::constraint))
                .put("invariantAggregate", InvariantAggregateJson.aggregate(view.invariantAggregate()))
                .put("unmatchedExceptions",
                        JsonSupport.array(view.unmatchedExceptions(), ExceptionJson::approvedException))
                .build();
    }

    private static JsonValue constraint(ViolatedConstraint constraint) {
        return new JsonObjectBuilder()
                .put("invariantName", constraint.invariantName())
                .put("severity", constraint.severity())
                .put("holds", constraint.holds())
                .put("indicatorValue", constraint.indicatorValue())
                .put("weight", JsonSupport.optionalDouble(constraint.weight()))
                .put("violations", JsonSupport.array(constraint.violations(), GovernanceViewJson::finding))
                .build();
    }

    /**
     * Question 5: what evidence supports each conclusion — by identifier.
     * The finding's own provenance stays under the metric that produced
     * it, so a reader resolves {@code subject} there rather than reading
     * a second copy that could disagree with the first.
     */
    private static JsonValue finding(TraceableFinding finding) {
        return new JsonObjectBuilder()
                .put("dimension", finding.dimension())
                .put("subject", finding.subject()
                        .map(ExceptionJson::target)
                        .orElse(JsonValue.JsonNull.INSTANCE))
                .putNullableString("governedBy", finding.governedBy().orElse(null))
                .put("excused", finding.isExcused())
                .put("excusedBy", finding.excusedBy()
                        .map(ExceptionJson::approvedException)
                        .orElse(JsonValue.JsonNull.INSTANCE))
                .build();
    }

    private static JsonValue strings(List<String> values) {
        return new JsonValue.JsonArray(
                values.stream().map(value -> (JsonValue) new JsonValue.JsonString(value)).toList());
    }
}
