package org.aerf.report;

import org.aerf.analysis.calibration.MaturityLevel;
import org.aerf.report.json.JsonValue;

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
}
