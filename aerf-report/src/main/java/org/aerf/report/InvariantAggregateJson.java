package org.aerf.report;

import org.aerf.analysis.calibration.InvariantAggregate;
import org.aerf.analysis.calibration.InvariantContribution;
import org.aerf.report.json.JsonObjectBuilder;
import org.aerf.report.json.JsonValue;

/**
 * Serializes AERF v0.4 §6.1's {@code E_inv = sum(lambda_k * I_k(S))}
 * (Increment 29, OQ-15).
 *
 * <p>The total is emitted beside every term that produced it, never
 * alone. A reader can check the sum against its own parts and see which
 * invariant contributed what — the serialized half of OQ-15's
 * requirement that no violation disappears through aggregation.
 *
 * <p>{@code value} is JSON {@code null} when the aggregate is undefined:
 * either no importance was declared, or a weighted invariant was never
 * evaluated. {@code unevaluatedWeightedInvariants} names the second case,
 * so an undefined total always arrives with its reason attached rather
 * than leaving a reader to guess which it was.
 */
public final class InvariantAggregateJson {

    private InvariantAggregateJson() {
    }

    public static JsonValue aggregate(InvariantAggregate aggregate) {
        return new JsonObjectBuilder()
                .put("value", JsonSupport.optionalDouble(aggregate.value()))
                .put("contributions",
                        JsonSupport.array(aggregate.contributions(), InvariantAggregateJson::contribution))
                .put("unevaluatedWeightedInvariants", new JsonValue.JsonArray(
                        aggregate.unevaluatedWeightedInvariants().stream()
                                .map(name -> (JsonValue) new JsonValue.JsonString(name))
                                .toList()))
                .build();
    }

    private static JsonValue contribution(InvariantContribution contribution) {
        return new JsonObjectBuilder()
                .put("invariantName", contribution.invariantName())
                .put("weight", contribution.weight())
                .put("indicatorValue", contribution.indicatorValue())
                .put("contribution", contribution.contribution())
                .build();
    }
}
