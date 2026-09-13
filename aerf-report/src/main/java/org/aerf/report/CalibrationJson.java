package org.aerf.report;

import org.aerf.analysis.calibration.MaturityLevel;
import org.aerf.report.json.JsonObjectBuilder;
import org.aerf.report.json.JsonValue;

import java.util.Map;
import java.util.OptionalDouble;

/**
 * Serializes calibration outputs (Increment 8, AERF v0.4 section 5).
 * There is deliberately no single combined "calibration report" object
 * here — {@code AggregatedEntropy}, {@code Maturity}, {@code MaturityLevel},
 * and {@code AnalysisConfidence} are independent pure functions with no
 * existing composite type bundling them, and inventing one to serialize
 * would be a schema decision this increment doesn't make (see the open
 * questions register on how invariant results, entropy, and drift should
 * eventually combine into one governance view).
 */
public final class CalibrationJson {

    private CalibrationJson() {
    }

    public static JsonValue totalEntropy(OptionalDouble value) {
        return JsonSupport.optionalDouble(value);
    }

    public static JsonValue maturity(OptionalDouble value) {
        return JsonSupport.optionalDouble(value);
    }

    public static JsonValue maturityLevel(MaturityLevel level) {
        return JsonSupport.string(level);
    }

    public static JsonValue confidence(OptionalDouble value) {
        return JsonSupport.optionalDouble(value);
    }

    /**
     * Per-dimension confidence (OQ-13), keyed by the same dimension names
     * the entropy values use. Serialized <em>alongside</em> the graph-wide
     * {@link #confidence(OptionalDouble)}, never in place of it: they
     * answer different questions and section 14's
     * "measurement before aggregation" principle keeps both visible.
     *
     * <p>An undefined dimension serializes as JSON {@code null}, not as a
     * missing key and not as {@code 0.0} — so a reader can tell "this
     * dimension has no confidence reading" (security, by construction)
     * apart from "nothing resolved" (a real 0.0).
     */
    public static JsonValue confidenceByDimension(Map<String, OptionalDouble> confidenceByDimension) {
        JsonObjectBuilder builder = new JsonObjectBuilder();
        confidenceByDimension.forEach((dimension, value) -> builder.put(dimension, JsonSupport.optionalDouble(value)));
        return builder.build();
    }
}
