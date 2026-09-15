package org.aerf.analysis.calibration;

import org.aerf.analysis.governance.DriftSensitivity;
import org.aerf.analysis.governance.WeightedDriftDimension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Computes AERF v0.4 §5.3's
 * {@code R = sum(w_d * f_d(E_d)) + beta * sum(gamma_d * max(0, Delta_d))}
 * (Increment 30, OQ-14).
 *
 * <p><b>A pure function, like {@link Drift} and
 * {@code AggregatedInvariants}, and for the same reason.</b> {@code R} is
 * drift-aware, so it needs a baseline — and {@code Pipeline} has neither a
 * subject identity nor a prior measurement, and deliberately does not
 * reach into storage to get one ({@code Drift}'s own javadoc: "Loading
 * two historical snapshots… is left to whichever caller already has that
 * access"). So {@code R} is not a {@code PipelineReport} component and
 * appears in no pipeline-produced JSON: a caller that has a baseline
 * computes it, exactly as {@code DriftEndToEndTest} already demonstrates
 * for drift.
 *
 * <p><b>Only worsening is penalized.</b> {@code Delta_d} is
 * {@code current - baseline} and higher entropy is worse, so §5.3's
 * {@code max(0, Delta_d)} means an improved dimension contributes zero —
 * it does not offset a regression elsewhere. Risk does not go down
 * because something unrelated got better.
 *
 * <p><b>Undefined-result policy.</b> Two existing policies meet here and
 * conflict: {@link AggregatedEntropy} poisons its whole aggregate when a
 * weighted dimension is undefined, while {@link Drift} silently omits
 * such a dimension. This class follows the first, as
 * {@code AggregatedInvariants} did before it:
 *
 * <ul>
 *   <li>an undefined {@code E_total} makes {@code R} undefined, since it
 *       is a term;
 *   <li>a dimension carrying <b>nonzero</b> {@code gamma_d} that is absent
 *       from the drift map — because it was undefined at either endpoint —
 *       makes {@code R} undefined <em>and is named</em>. Treating it as a
 *       zero penalty would claim "no worsening observed" where in fact
 *       nothing was comparable;
 *   <li>{@code gamma_d = 0} is exempt, since it does not actually
 *       contribute to the sum;
 *   <li>no declared sensitivity at all leaves {@code R} undefined rather
 *       than silently returning {@code E_total} under a different name.
 * </ul>
 *
 * <p><b>It refuses across a policy change.</b> If the two snapshots'
 * governance fingerprints differ, or either is absent, {@code R} is
 * undefined with a stated reason. A risk number computed across a silent
 * policy change is worse than no number: the "drift" it reports may be
 * nothing but a changed layering matrix. Note the asymmetry documented on
 * {@code GovernanceFingerprint} — differing fingerprints prove the
 * policies differ, but matching ones do not prove they are the same.
 */
public final class Risk {

    private Risk() {
    }

    /**
     * @param totalEntropy §5.1's {@code E_total} as the current run already
     *                     computed it — passed in rather than recomputed, so
     *                     the report's own number and {@code R}'s first term
     *                     can never disagree
     * @param drift        {@link Drift#compute}'s output for these two snapshots
     */
    public static RiskAssessment compute(OptionalDouble totalEntropy,
                                         EntropySnapshot baseline,
                                         EntropySnapshot current,
                                         Map<String, DimensionDrift> drift,
                                         DriftSensitivity sensitivity) {
        Objects.requireNonNull(totalEntropy, "totalEntropy");
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(drift, "drift");
        Objects.requireNonNull(sensitivity, "sensitivity");

        List<String> undefinedBecause = new ArrayList<>();
        governanceMismatch(baseline, current).ifPresent(undefinedBecause::add);

        if (sensitivity.isEmpty()) {
            undefinedBecause.add("no drift sensitivity was declared, so section 5.3's second term is unknown");
        }
        if (totalEntropy.isEmpty()) {
            undefinedBecause.add("total entropy is undefined, so section 5.3's first term is unknown");
        }

        List<DriftPenalty> penalties = new ArrayList<>();
        double penaltySum = 0.0;
        for (WeightedDriftDimension weighted : sensitivity.declared()) {
            DimensionDrift dimensionDrift = drift.get(weighted.dimension());
            if (dimensionDrift == null) {
                if (weighted.weight() != 0.0) {
                    undefinedBecause.add("dimension '" + weighted.dimension() + "' carries drift weight "
                            + weighted.weight() + " but was not comparable between the two measurements");
                }
                continue;
            }
            double penalty = weighted.weight() * Math.max(0.0, dimensionDrift.delta());
            penalties.add(new DriftPenalty(
                    weighted.dimension(), weighted.weight(), dimensionDrift.delta(), penalty));
            penaltySum += penalty;
        }

        if (!undefinedBecause.isEmpty()) {
            // The terms that were computable stay visible even when the
            // total is not: hiding them would lose the evidence a reader
            // needs to see why.
            return new RiskAssessment(
                    OptionalDouble.empty(), totalEntropy, OptionalDouble.empty(), penalties, undefinedBecause);
        }

        double driftTerm = sensitivity.beta().getAsDouble() * penaltySum;
        return new RiskAssessment(
                OptionalDouble.of(totalEntropy.getAsDouble() + driftTerm),
                totalEntropy,
                OptionalDouble.of(driftTerm),
                penalties,
                List.of());
    }

    private static Optional<String> governanceMismatch(EntropySnapshot baseline, EntropySnapshot current) {
        Optional<String> baselineFingerprint = baseline.governanceFingerprint();
        Optional<String> currentFingerprint = current.governanceFingerprint();

        if (baselineFingerprint.isEmpty() || currentFingerprint.isEmpty()) {
            return Optional.of("the governance policy behind at least one measurement is unrecorded, so the two "
                    + "cannot be shown comparable - unknown is not the same as matching");
        }
        if (!baselineFingerprint.get().equals(currentFingerprint.get())) {
            return Optional.of("the two measurements were governed by different policies, so their difference "
                    + "is not necessarily code drift");
        }
        return Optional.empty();
    }
}
