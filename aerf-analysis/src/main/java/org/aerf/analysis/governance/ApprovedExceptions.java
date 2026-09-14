package org.aerf.analysis.governance;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Every {@link ApprovedException} an organization has declared, in
 * declaration order, validated so that at most one can ever claim a
 * given finding (Increment 28, OQ-09).
 *
 * <p>Two exceptions naming the same target are rejected at construction.
 * An excused finding has an owner, and two owners for one finding is an
 * ambiguity nothing downstream could resolve — the same reason
 * {@link Subsystems} refuses overlapping prefixes rather than picking a
 * winner.
 *
 * <p>An empty instance is the ordinary case and means exactly what it
 * says: nothing has been excused, so every finding stands on its own.
 */
public final class ApprovedExceptions {

    private static final ApprovedExceptions NONE = new ApprovedExceptions(List.of());

    private final List<ApprovedException> declared;

    private ApprovedExceptions(List<ApprovedException> declared) {
        this.declared = List.copyOf(Objects.requireNonNull(declared, "declared"));
        validateNoTargetIsClaimedTwice(this.declared);
    }

    public static ApprovedExceptions of(List<ApprovedException> declared) {
        return new ApprovedExceptions(declared);
    }

    /** Nothing excused: every finding stands on its own. */
    public static ApprovedExceptions none() {
        return NONE;
    }

    /** In declaration order — the organization's own, never re-sorted. */
    public List<ApprovedException> declared() {
        return declared;
    }

    public boolean isEmpty() {
        return declared.isEmpty();
    }

    /**
     * The exception admitting this finding, or empty when none does.
     * Construction guarantees at most one match, so this is independent
     * of declaration order.
     */
    public Optional<ApprovedException> excusing(ExceptionTarget target) {
        Objects.requireNonNull(target, "target");
        return declared.stream().filter(e -> e.target().equals(target)).findFirst();
    }

    private static void validateNoTargetIsClaimedTwice(List<ApprovedException> declared) {
        Set<ExceptionTarget> seen = new HashSet<>();
        List<ExceptionTarget> duplicates = new ArrayList<>();
        for (ApprovedException exception : declared) {
            if (!seen.add(exception.target())) {
                duplicates.add(exception.target());
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException(
                    "more than one approved exception targets the same finding, so it would have two owners: "
                            + duplicates);
        }
    }
}
