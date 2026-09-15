package org.aerf.analysis.governance;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Every {@link WeightedInvariant} an organization has declared, in
 * declaration order (Increment 29, OQ-15) — the λ_k side of
 * {@code E_inv = sum(lambda_k * I_k(S))}.
 *
 * <p>Two weights for one invariant are rejected at construction: an
 * invariant has one importance, and two would leave the aggregate
 * depending on which was read first. The same reasoning
 * {@link ApprovedExceptions} applies to two owners for one finding.
 *
 * <p><b>An empty instance leaves {@code E_inv} undefined, not zero.</b>
 * An organization that has declared no importances has not said that its
 * invariants are unimportant; it has said nothing, and undefined is how
 * this project represents that everywhere else.
 *
 * <p>Note there is deliberately no sum-to-one validation here, which is
 * the one structural difference from {@code CalibrationProfile}: §6.1
 * imposes no normalization constraint on λ_k. That also makes an empty
 * instance representable at all, which an empty {@code CalibrationProfile}
 * is not.
 */
public final class InvariantWeights {

    private static final InvariantWeights NONE = new InvariantWeights(List.of());

    private final List<WeightedInvariant> declared;

    private InvariantWeights(List<WeightedInvariant> declared) {
        this.declared = List.copyOf(Objects.requireNonNull(declared, "declared"));
        validateNoInvariantIsWeightedTwice(this.declared);
    }

    public static InvariantWeights of(List<WeightedInvariant> declared) {
        return new InvariantWeights(declared);
    }

    /** No importance declared: {@code E_inv} is undefined for this run. */
    public static InvariantWeights none() {
        return NONE;
    }

    /** In declaration order — the organization's own, never re-sorted. */
    public List<WeightedInvariant> declared() {
        return declared;
    }

    public boolean isEmpty() {
        return declared.isEmpty();
    }

    /**
     * This invariant's λ_k, or empty when it carries none. Construction
     * guarantees at most one match, so this is order-independent.
     */
    public OptionalDouble weightFor(String invariantName) {
        Objects.requireNonNull(invariantName, "invariantName");
        return declared.stream()
                .filter(weighted -> weighted.invariantName().equals(invariantName))
                .mapToDouble(WeightedInvariant::weight)
                .findFirst();
    }

    private static void validateNoInvariantIsWeightedTwice(List<WeightedInvariant> declared) {
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (WeightedInvariant weighted : declared) {
            if (!seen.add(weighted.invariantName())) {
                duplicates.add(weighted.invariantName());
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException(
                    "an invariant may carry only one weight, but these are weighted more than once: " + duplicates);
        }
    }
}
