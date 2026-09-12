package org.aerf.report;

import org.aerf.analysis.calibration.DimensionDrift;
import org.aerf.report.json.JsonObjectBuilder;
import org.aerf.report.json.JsonValue;

import java.util.Map;

/**
 * Serializes {@link org.aerf.analysis.calibration.Drift}'s output (AERF
 * v0.4 section 5.3) - the last piece of the v0.4 contract reconciliation's
 * V04-CAL-02 remediation: {@code Drift.compute} and
 * {@code PipelineReport.toEntropySnapshot} make drift computable from a
 * real pipeline run, and this class is what lets a computed drift join
 * the rest of a report's JSON output, the same way every other metric in
 * this package does.
 *
 * <p>Deliberately a standalone mapper, not folded into a single combined
 * "calibration report" object - see {@link CalibrationJson}'s own
 * javadoc on why one doesn't exist here: how drift eventually combines
 * with entropy and invariant results into one governance view is still
 * open (open-questions-register #14/#16). This class only serializes a
 * drift result a caller already computed; it does not decide where in a
 * larger report that result belongs.
 */
public final class DriftJson {

    private DriftJson() {
    }

    public static JsonValue drift(Map<String, DimensionDrift> drift) {
        JsonObjectBuilder builder = new JsonObjectBuilder();
        for (Map.Entry<String, DimensionDrift> entry : drift.entrySet()) {
            builder.put(entry.getKey(), dimensionDrift(entry.getValue()));
        }
        return builder.build();
    }

    private static JsonValue dimensionDrift(DimensionDrift drift) {
        return new JsonObjectBuilder()
                .put("baselineValue", drift.baselineValue())
                .put("currentValue", drift.currentValue())
                .put("delta", drift.delta())
                .build();
    }
}
