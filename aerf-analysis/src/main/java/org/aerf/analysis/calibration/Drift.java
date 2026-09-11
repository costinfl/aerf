package org.aerf.analysis.calibration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Computes {@code Delta_d = E_d^t - E_d^0} (AERF v0.4 section 5.3):
 * baseline-relative drift for every entropy dimension defined in both a
 * baseline and a current {@link EntropySnapshot} of the same subject.
 *
 * <p><b>Remediation record:</b> v0.4 section 5.3 explicitly specifies
 * this formula, but no implementation existed anywhere in the reactor
 * before this class - confirmed by searching the codebase for any
 * drift/baseline concept in {@code aerf-analysis}/{@code aerf-pipeline}
 * before adding it. The v0.4.1 status report recorded that Increment 19
 * built a place to *store* historical measurements (the dashboard's
 * Supabase {@code scans} table), but nothing in the Java pipeline ever
 * computed a delta between two of them - v0.4's own §5.3 contract was
 * therefore unimplemented, not merely undecided, which is why this is a
 * remediation rather than a v0.4.2 feature request (see
 * {@code docs/aerf-v0.4-reconciliation-evidence.md}, item V04-CAL-02).
 *
 * <p>This class deliberately stops at the pure calculation §5.3 defines.
 * It does not reach into Supabase or any other store itself: no other
 * class in {@code aerf-pipeline}/{@code aerf-analysis} performs network
 * or database I/O ({@code Pipeline.run} is a single, self-contained
 * extraction over local source), and giving this one calculation a
 * different shape than every other calculator in this package - which
 * are all pure functions of values the caller already has in hand -
 * would be a larger, unrequested design decision, not the "smallest
 * compensating implementation change" the reconciliation instructions
 * ask for. Loading two historical snapshots to build the {@link
 * EntropySnapshot} inputs is left to whichever caller already has that
 * access, the same way {@link AggregatedEntropy} does not itself decide
 * where {@code CalibrationProfile} weights come from.
 *
 * <p>A dimension undefined in either snapshot contributes no entry to
 * the result at all - not a zero delta - matching {@link
 * AggregatedEntropy}'s own "undefined stays undefined" policy: a
 * dimension AERF could not measure at either point in time says nothing
 * about how it changed.
 */
public final class Drift {

    private Drift() {
    }

    /**
     * @throws IllegalArgumentException if {@code baseline} and {@code current}
     *     do not name the same subject - drift is only meaningful between two
     *     measurements of the same system over time (section 5.3), so this
     *     never silently diffs unrelated measurements.
     */
    public static Map<String, DimensionDrift> compute(EntropySnapshot baseline, EntropySnapshot current) {
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(current, "current");
        if (!baseline.subjectId().equals(current.subjectId())) {
            throw new IllegalArgumentException(
                    "drift requires baseline and current measurements of the same subject (AERF v0.4 section 5.3): "
                            + "baseline subject '" + baseline.subjectId() + "' != current subject '"
                            + current.subjectId() + "'");
        }

        Map<String, DimensionDrift> result = new LinkedHashMap<>();
        for (Map.Entry<String, OptionalDouble> entry : current.dimensionValues().entrySet()) {
            String dimension = entry.getKey();
            OptionalDouble currentValue = entry.getValue();
            OptionalDouble baselineValue = baseline.dimensionValues().get(dimension);
            if (baselineValue == null || baselineValue.isEmpty() || currentValue.isEmpty()) {
                continue;
            }
            double b = baselineValue.getAsDouble();
            double c = currentValue.getAsDouble();
            result.put(dimension, new DimensionDrift(dimension, b, c, c - b));
        }
        // Map.copyOf does not guarantee it preserves a LinkedHashMap's
        // iteration order (see aerf-v0.4.1-patch.md's non-normative note on
        // Increment 1's own GraphTest regression) - wrap explicitly instead.
        return Collections.unmodifiableMap(result);
    }
}
