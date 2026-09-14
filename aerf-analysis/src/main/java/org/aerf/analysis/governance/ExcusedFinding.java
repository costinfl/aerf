package org.aerf.analysis.governance;

import java.util.Objects;

/**
 * One observed finding matched to the {@link ApprovedException} that
 * admits it (Increment 28, OQ-09).
 *
 * <p>This records a <em>relation between</em> a declaration and an
 * observation. It is deliberately not written onto the finding, the edge,
 * or its evidence: {@code Evidence}'s contract is that it "always
 * represents something that was actually observed", and a governance
 * declaration is not an observation. Keeping excusal in a separate ledger
 * is what lets the constraint "never silently delete or mutate source
 * evidence" hold by construction rather than by care.
 *
 * @param dimension which measurement produced the finding — the same keys
 *                  {@code Pipeline} already uses ({@code layer},
 *                  {@code persistence}, {@code security}), or
 *                  {@code invariant:<name>} for an invariant violation
 */
public record ExcusedFinding(String dimension, ExceptionTarget target, ApprovedException exception) {

    public ExcusedFinding {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(exception, "exception");
        if (dimension.isBlank()) {
            throw new IllegalArgumentException("dimension must not be blank");
        }
    }

    /** Who accepted this finding — the accountability an exception register exists to provide. */
    public String approvedBy() {
        return exception.approvedBy();
    }

    /** Why it was accepted. */
    public String reason() {
        return exception.reason();
    }
}
