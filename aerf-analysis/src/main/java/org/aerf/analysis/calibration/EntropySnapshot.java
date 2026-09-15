package org.aerf.analysis.calibration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * One measurement of every entropy dimension for a single subject (e.g. a
 * scanned project) at some point in time - the input {@link Drift} needs
 * to compute AERF v0.4 section 5.3's {@code Delta_d = E_d^t - E_d^0}.
 *
 * <p>{@code dimensionValues} uses the same shape {@link
 * AggregatedEntropy#compute} already takes: a dimension name (e.g.
 * {@code "layer"}, {@code "persistence"} - {@code Pipeline}'s own keys)
 * mapped to that dimension's value, {@code OptionalDouble.empty()} when
 * the dimension was undefined for this measurement rather than zero (the
 * same "undefined stays undefined" policy every entropy result in this
 * project already follows).
 *
 * <p>{@code subjectId} names <em>what</em> was measured, not when - two
 * snapshots of the same subject taken at different times are exactly
 * what {@link Drift} compares. It deliberately mirrors the dashboard's
 * own {@code project_id} column (Increment 19's Supabase schema) rather
 * than inventing a new identifier shape, since that is the one place
 * this project already persists more than one measurement of the same
 * subject over time.
 *
 * <p>{@code governanceFingerprint} (Increment 30, OQ-14) records which
 * governance policy produced this measurement, closing the gap Increment
 * 25 recorded: without it, comparing a stored baseline against a current
 * scan is only sound if the policy was identical, and nothing in the data
 * said whether it was — so a policy change could read as code drift.
 * {@code Drift} still compares values alone, because a numeric difference
 * is arithmetically sound whatever produced it; it is
 * {@link Risk}, which <em>interprets</em> that difference, that refuses
 * to proceed across a policy change. See {@code GovernanceFingerprint}
 * on why matching fingerprints prove less than differing ones.
 */
public record EntropySnapshot(
        String subjectId,
        Map<String, OptionalDouble> dimensionValues,
        Optional<String> governanceFingerprint) {

    /**
     * A measurement whose governing policy was not recorded — a baseline
     * stored before Increment 30, for instance. Deliberately named rather
     * than defaulted: "the policy is unknown" is a different statement
     * from "the policy matched", and {@code Risk} treats it as such.
     */
    public static EntropySnapshot withoutGovernanceIdentity(
            String subjectId, Map<String, OptionalDouble> dimensionValues) {
        return new EntropySnapshot(subjectId, dimensionValues, Optional.empty());
    }

    public EntropySnapshot {
        Objects.requireNonNull(governanceFingerprint, "governanceFingerprint");
        Objects.requireNonNull(subjectId, "subjectId");
        if (subjectId.isBlank()) {
            throw new IllegalArgumentException("subjectId must not be blank");
        }
        Objects.requireNonNull(dimensionValues, "dimensionValues");
        // Map.copyOf does not guarantee it preserves a source map's
        // iteration order (see aerf-v0.4.1-patch.md's non-normative note
        // on Increment 1's own GraphTest regression) - wrap explicitly
        // instead, since this order ultimately reaches DriftJson's
        // serialized output and section 14 requires identical sources to
        // produce identical output across runs, not just within one.
        dimensionValues = Collections.unmodifiableMap(new LinkedHashMap<>(dimensionValues));
    }
}
