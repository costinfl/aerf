package org.aerf.analysis.governance;

import java.util.List;
import java.util.Objects;

/**
 * The governance verdict on one run's findings (Increment 28, OQ-09):
 * which of them an organization has already accepted, and which of its
 * declared exceptions matched nothing.
 *
 * <p>This is the <em>only</em> thing an approved exception affects.
 * Answering the commission's own question — whether suppression affects
 * entropy, findings, risk, or only governance presentation — the answer
 * is governance presentation, alone. Every entropy value is exactly what
 * it was, every finding is still in its result list with its evidence
 * intact, and risk is OQ-14's to define; this ledger is what that model
 * is expected to consume.
 *
 * <p>{@link #unmatched()} is not an afterthought. An exception that
 * matches no finding is first-class governance information: either the
 * code was fixed and the exception is now stale, or it never described a
 * real finding in the first place. Reporting it explicitly rather than
 * letting it vanish follows the same discipline as {@code
 * PipelineReport.skippedInvariants} — §5.4's principle that incomplete or
 * inapplicable evidence must stay visible rather than disappearing.
 *
 * <p>Note what this cannot tell you today: an exception that <em>is</em>
 * matched may still be years old and unreviewed. Exceptions carry no
 * expiry, because nothing in AERF reads a clock and "expired" would need
 * semantics the risk model has not defined.
 */
public record ExceptionLedger(List<ExcusedFinding> excused, List<ApprovedException> unmatched) {

    private static final ExceptionLedger EMPTY = new ExceptionLedger(List.of(), List.of());

    public ExceptionLedger {
        excused = List.copyOf(Objects.requireNonNull(excused, "excused"));
        unmatched = List.copyOf(Objects.requireNonNull(unmatched, "unmatched"));
    }

    /** Nothing declared, so nothing excused and nothing stale. */
    public static ExceptionLedger empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return excused.isEmpty() && unmatched.isEmpty();
    }
}
