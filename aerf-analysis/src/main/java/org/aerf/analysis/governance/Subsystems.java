package org.aerf.analysis.governance;

import org.aerf.model.NodeId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Every {@link Subsystem} an organization has declared, in declaration
 * order, validated so that exactly one of them can ever claim a given
 * node (Increment 26, OQ-04; extended to cycle entropy in Increment 27,
 * OQ-06).
 *
 * <p>This is the single declaration both scoped dimensions read, so a
 * node's subsystem cannot depend on which metric is asking.
 *
 * <p><b>Overlapping selectors are rejected, not resolved.</b> Declaring
 * both {@code com.foo} and {@code com.foo.bar} throws at construction
 * rather than being settled by a most-specific-wins rule. Resolving by
 * prefix length would be a <em>derivation</em> — the same kind of
 * mechanical inference {@code LayerPolicy} explicitly refuses when it
 * declines to build a matrix from an ordering — and it would let a typo
 * in one prefix silently reassign nodes to a neighbouring subsystem
 * instead of failing. This follows the project's existing stance that an
 * ambiguous declaration is rejected rather than quietly normalized (see
 * {@code SecurityConcern.requireCanonical}).
 *
 * <p><b>Recorded limitation:</b> "everything in {@code com.foo} except
 * {@code com.foo.bar}" therefore cannot be expressed. Nested subsystems
 * need a containment semantics this increment does not decide.
 *
 * <p>An empty instance is the ordinary case and means exactly what it
 * says: no subsystem was declared, so one matrix governs the whole graph
 * — byte-for-byte the behaviour that existed before OQ-04, on the same
 * code path rather than a parallel one.
 */
public final class Subsystems {

    private static final Subsystems NONE = new Subsystems(List.of());

    private final List<Subsystem> declared;

    private Subsystems(List<Subsystem> declared) {
        this.declared = List.copyOf(Objects.requireNonNull(declared, "declared"));
        validateNamesAreUnique(this.declared);
        validateNoPrefixClaimsAnother(this.declared);
    }

    public static Subsystems of(List<Subsystem> declared) {
        return new Subsystems(declared);
    }

    /** No subsystem declared: one matrix governs everything. */
    public static Subsystems none() {
        return NONE;
    }

    /** In declaration order — the organization's own, never re-sorted. */
    public List<Subsystem> declared() {
        return declared;
    }

    public boolean isEmpty() {
        return declared.isEmpty();
    }

    /**
     * The subsystem claiming this node, or empty when none does — in
     * which case the caller applies the default matrix. Construction
     * guarantees at most one match, so this is order-independent.
     */
    public Optional<Subsystem> governing(NodeId id) {
        Objects.requireNonNull(id, "id");
        for (Subsystem candidate : declared) {
            if (candidate.matches(id)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * The subsystem whose <em>own layering matrix</em> judges a node, or
     * empty when the default matrix does (Increment 31, OQ-16).
     *
     * <p>This is deliberately a different question from
     * {@link #governing(NodeId)}, and the difference is the whole reason
     * this method exists. A node claimed by a subsystem that declared no
     * matrix of its own is <em>claimed</em> by that subsystem but
     * <em>judged</em> by the default — so answering "which subsystem
     * judged this violation?" with the claimant would name a declaration
     * that had no part in the verdict.
     *
     * <p>{@code LayerEntropyCalculator} selects the judging matrix by
     * exactly this rule and now calls this method to do it, so the
     * attribution a reader sees and the matrix that actually produced the
     * violation cannot drift apart. Increment 26 chose the source as the
     * deciding endpoint; that choice lives in the calculator, since which
     * endpoint to ask is a measurement question, while which matrix
     * answers for a given node is this class's own.
     */
    public Optional<Subsystem> governingMatrix(NodeId id) {
        return governing(id).filter(subsystem -> subsystem.layerPolicy().isPresent());
    }

    private static void validateNamesAreUnique(List<Subsystem> declared) {
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (Subsystem policy : declared) {
            if (!seen.add(policy.name())) {
                duplicates.add(policy.name());
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("duplicate subsystem name(s): " + duplicates);
        }
    }

    private static void validateNoPrefixClaimsAnother(List<Subsystem> declared) {
        for (Subsystem one : declared) {
            for (Subsystem other : declared) {
                if (one == other) {
                    continue;
                }
                if (other.idPrefix().startsWith(one.idPrefix())) {
                    throw new IllegalArgumentException(
                            "subsystem '" + one.name() + "' (idPrefix '" + one.idPrefix() + "') would also claim "
                                    + "nodes of '" + other.name() + "' (idPrefix '" + other.idPrefix() + "'); "
                                    + "declare disjoint prefixes - nesting is not resolved by specificity");
                }
            }
        }
    }
}
