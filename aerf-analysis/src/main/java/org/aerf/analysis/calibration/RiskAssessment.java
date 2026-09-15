package org.aerf.analysis.calibration;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * AERF v0.4 §5.3's
 * {@code R = sum(w_d * f_d(E_d)) + beta * sum(gamma_d * max(0, Delta_d))},
 * together with the parts that produced it (Increment 30, OQ-14).
 *
 * <p><b>Deliberately not an opaque single score.</b> OQ-14's commission
 * says so outright, and the shape here is the answer: the entropy term
 * and the drift term stay separately visible beside the total, and every
 * per-dimension penalty stays visible beside the drift term. A reader can
 * always tell whether a high {@code R} means "this architecture is in
 * poor shape" or "this architecture got worse recently" — two very
 * different situations that a bare number would conflate.
 *
 * <p><b>Invariant violations are not a term here.</b> §5.3 states exactly
 * two terms and says nothing about them, and the standing rule is that
 * entropy, drift and violations stay separately visible rather than
 * collapsing into one architecture score. {@code E_inv} and the exception
 * ledger are reported <em>beside</em> {@code R}; composing all of them
 * into one governance-facing view without merging them is OQ-16's job.
 *
 * <p>{@link #undefinedBecause()} names every reason {@link #value()} is
 * empty — a policy change between the two measurements, an undefined
 * {@code E_total}, or a weighted dimension that was not comparable. An
 * undefined risk always arrives with its reasons attached rather than
 * leaving a reader to guess which applied.
 */
public record RiskAssessment(
        OptionalDouble value,
        OptionalDouble entropyTerm,
        OptionalDouble driftTerm,
        List<DriftPenalty> penalties,
        List<String> undefinedBecause) {

    public RiskAssessment {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(entropyTerm, "entropyTerm");
        Objects.requireNonNull(driftTerm, "driftTerm");
        penalties = List.copyOf(Objects.requireNonNull(penalties, "penalties"));
        undefinedBecause = List.copyOf(Objects.requireNonNull(undefinedBecause, "undefinedBecause"));
        if (value.isPresent() && !undefinedBecause.isEmpty()) {
            throw new IllegalArgumentException(
                    "a defined risk cannot also carry reasons it is undefined: " + undefinedBecause);
        }
    }
}
