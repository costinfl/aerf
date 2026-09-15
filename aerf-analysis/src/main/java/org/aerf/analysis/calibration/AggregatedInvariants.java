package org.aerf.analysis.calibration;

import org.aerf.analysis.governance.InvariantWeights;
import org.aerf.analysis.governance.WeightedInvariant;
import org.aerf.analysis.invariant.InvariantEvaluationResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Computes {@code E_inv = sum(lambda_k * I_k(S))} (AERF v0.4 §6.1) from
 * declared {@link InvariantWeights} and one run's invariant results
 * (Increment 29, OQ-15).
 *
 * <p>Deliberately a sibling of {@link AggregatedEntropy}, because the
 * semantics are the same transplanted onto a different formula — the
 * package layout is part of the point.
 *
 * <p><b>Unbounded, exactly as §6.1 states.</b> §5.1 constrains entropy
 * weights with {@code sum(w_d) = 1}; §6.1 states no such constraint on
 * λ_k, so this is a plain weighted sum with no denominator and no clamp.
 * Inventing a normalization would assert something the specification
 * declines to. The consequence is that {@code E_inv} is <em>not</em> an
 * entropy dimension and must never be handed to {@link AggregatedEntropy}
 * — see {@link InvariantAggregate}.
 *
 * <p><b>Undefined-result policy, mirroring {@link AggregatedEntropy}'s
 * word for word.</b> An invariant that was skipped — {@code Pipeline}
 * does this when a GRAPH-scope invariant references a metric the run left
 * undefined — produced no {@code I_k(S)} at all. That is the exact
 * analogue of an undefined entropy dimension, so if such an invariant
 * carries nonzero λ_k the whole aggregate is undefined; weighted
 * {@code 0} it is exempt, since it does not actually contribute to the
 * sum. Coercing a missing indicator to {@code 0} would claim "this
 * invariant holds" where in fact it was never checked.
 *
 * <p><b>Declared weights must cover every configured invariant.</b>
 * Anything else is a hidden default: an unweighted invariant would
 * silently contribute nothing, so a violation could vanish from the
 * aggregate merely because nobody assigned it an importance — precisely
 * what OQ-15's "no violation disappears through aggregation" forbids.
 */
public final class AggregatedInvariants {

    private AggregatedInvariants() {
    }

    /**
     * @param weights           the declared λ_k; empty leaves the aggregate undefined
     * @param evaluated         every invariant this run actually evaluated
     * @param skippedInvariants the names of invariants configured but not evaluated
     *                          — {@code PipelineReport.skippedInvariants()}'s own list
     * @throws IllegalArgumentException if weights are declared but do not cover
     *                                  every configured invariant, or weight an
     *                                  invariant that was never configured
     */
    public static InvariantAggregate compute(InvariantWeights weights,
                                             List<InvariantEvaluationResult> evaluated,
                                             List<String> skippedInvariants) {
        Objects.requireNonNull(weights, "weights");
        Objects.requireNonNull(evaluated, "evaluated");
        Objects.requireNonNull(skippedInvariants, "skippedInvariants");

        if (weights.isEmpty()) {
            return InvariantAggregate.undeclared();
        }

        Map<String, InvariantEvaluationResult> byName = new LinkedHashMap<>();
        for (InvariantEvaluationResult result : evaluated) {
            byName.put(result.invariantName(), result);
        }
        requireCompleteCoverage(weights, byName.keySet(), skippedInvariants);

        List<InvariantContribution> contributions = new ArrayList<>();
        List<String> unevaluated = new ArrayList<>();
        double total = 0.0;
        boolean defined = true;

        for (WeightedInvariant weighted : weights.declared()) {
            InvariantEvaluationResult result = byName.get(weighted.invariantName());
            if (result == null) {
                // Configured but not evaluated. Exempt at weight 0, exactly
                // as AggregatedEntropy exempts a zero-weighted dimension.
                if (weighted.weight() != 0.0) {
                    unevaluated.add(weighted.invariantName());
                    defined = false;
                }
                continue;
            }
            int indicator = result.indicatorValue();
            double contribution = weighted.weight() * indicator;
            contributions.add(new InvariantContribution(
                    weighted.invariantName(), weighted.weight(), indicator, contribution));
            total += contribution;
        }

        // Contributions are reported even when the total is undefined: the
        // invariants that were evaluated still have real indicator values,
        // and hiding them would lose exactly the evidence OQ-15 requires to
        // stay visible.
        return new InvariantAggregate(
                defined ? OptionalDouble.of(total) : OptionalDouble.empty(), contributions, unevaluated);
    }

    private static void requireCompleteCoverage(InvariantWeights weights, Set<String> evaluatedNames,
                                                List<String> skippedInvariants) {
        List<String> configured = new ArrayList<>(evaluatedNames);
        configured.addAll(skippedInvariants);

        List<String> unweighted = configured.stream()
                .filter(name -> weights.weightFor(name).isEmpty())
                .toList();
        if (!unweighted.isEmpty()) {
            throw new IllegalArgumentException(
                    "every configured invariant needs a declared weight once any is declared, or a violation could "
                            + "vanish from E_inv merely because nobody weighted it; missing: " + unweighted);
        }

        List<String> unknown = weights.declared().stream()
                .map(WeightedInvariant::invariantName)
                .filter(name -> !configured.contains(name))
                .toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(
                    "these invariants carry a weight but were never configured, so the weight names nothing: "
                            + unknown);
        }
    }
}
