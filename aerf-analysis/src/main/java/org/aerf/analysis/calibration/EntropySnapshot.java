package org.aerf.analysis.calibration;

import java.util.Map;
import java.util.Objects;
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
 */
public record EntropySnapshot(String subjectId, Map<String, OptionalDouble> dimensionValues) {

    public EntropySnapshot {
        Objects.requireNonNull(subjectId, "subjectId");
        if (subjectId.isBlank()) {
            throw new IllegalArgumentException("subjectId must not be blank");
        }
        dimensionValues = Map.copyOf(Objects.requireNonNull(dimensionValues, "dimensionValues"));
    }
}
