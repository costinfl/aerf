package org.aerf.analysis.view;

import org.aerf.analysis.calibration.DimensionDrift;
import org.aerf.analysis.calibration.RiskAssessment;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The two baseline-relative answers together (Increment 31, OQ-16): the
 * commission's second question, "what changed relative to baseline", and
 * its fourth, "what risk interpretation follows".
 *
 * <p><b>They are one value because they share one precondition.</b>
 * Neither exists without a caller-supplied baseline, and
 * {@code Optional<BaselineComparison>} says that once. Two separate
 * optional fields could express "drift present, risk absent", which is
 * not a state this project has: {@code Risk.compute} always returns an
 * assessment, undefined-with-reasons where it cannot conclude, so a
 * baseline always yields both.
 *
 * <p>The distinction the {@code Optional} preserves is the one that
 * matters: <b>"no baseline was supplied" is not "nothing changed".</b>
 * Two empty collections would read identically for both, and a governance
 * reader would have no way to tell an unchanged system from an
 * uncompared one.
 *
 * <p><b>There is deliberately no baseline subject identifier here.</b>
 * {@code Drift.compute} rejects two snapshots that do not name the same
 * subject — §5.3's drift is one system over time, never two systems — so
 * a baseline's subject is always the enclosing {@link
 * GovernanceView#subjectId()} and a second copy of it would only invite
 * the reader to imagine it could differ. What genuinely can differ
 * between the two measurements is the <em>governance</em> behind them, and
 * that difference is reported where it belongs: as {@code R}'s own stated
 * refusal.
 *
 * @param drift {@code Drift.compute}'s output, per dimension, each entry keeping its
 *              baseline and current values beside the delta. A dimension undefined at
 *              either endpoint is absent rather than zeroed — {@code Drift}'s own
 *              semantics, carried through unchanged
 * @param risk  §5.3's {@code R} with both terms and every per-dimension penalty.
 *              Undefined with stated reasons where {@code Risk} refused, including
 *              across a governance change
 */
public record BaselineComparison(
        Map<String, DimensionDrift> drift,
        RiskAssessment risk) {

    public BaselineComparison {
        Objects.requireNonNull(risk, "risk");
        // LinkedHashMap, not Map.copyOf: this map reaches serialized output,
        // and Map.copyOf's iteration order is unspecified, which would make
        // identical input produce differently-ordered JSON across runs
        // (section 14). The same copy PipelineReport.confidenceByDimension
        // and entropyByDimension already make, for the same reason.
        drift = Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(drift, "drift")));
    }
}
