package org.aerf.analysis.governance;

import java.util.Objects;

/**
 * One finding an organization has looked at and decided to live with
 * (Increment 28, OQ-09) — AERF v0.4 §4.3's "batched or otherwise
 * justified" clause, and the same admission for any other node- or
 * edge-shaped finding.
 *
 * <p><b>An approved exception is an acceptance of a finding, never a
 * denial of it, and it changes no measured value.</b> The finding stays
 * in its result list, the entropy it contributes to stays exactly as
 * measured, and the evidence behind it is untouched. What an exception
 * changes is the governance verdict: {@link ExceptionLedger} records
 * that this finding is excused, by whom, and why.
 *
 * <p>Two reasons that is the right side of the line. First, drift is
 * already implemented: if approving an exception lowered entropy, then
 * approving one would register as <em>code improvement</em> against a
 * stored baseline — the architecture would look better because somebody
 * signed a form. Second, AERF cannot detect batching, so the only route
 * to "this repetition is justified" is a human assertion, and letting a
 * governance declaration overrule an extractor's observation is exactly
 * what this project refused for cycle relevance in Increment 27. A
 * "this is actually batched" claim is better evidence for improving the
 * heuristic, with a test, than for editing a number.
 *
 * <p><b>All three fields are required.</b> An exception with no stated
 * reason, or no named approver, is not an approved exception — it is an
 * unexplained hole, and the compiler says so. This is the same stance
 * {@link GovernancePolicy} takes in refusing to let any governance
 * choice be made by omission.
 *
 * @param target     the finding being admitted
 * @param reason     why the organization accepts it — free text, because no
 *                   closed vocabulary of justifications is defensible here any
 *                   more than it was for an invariant's severity
 * @param approvedBy who accepted it, so the decision has an owner
 */
public record ApprovedException(ExceptionTarget target, String reason, String approvedBy) {

    public ApprovedException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(approvedBy, "approvedBy");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank: an exception without a stated "
                    + "justification is an unexplained hole, not an approved exception");
        }
        if (approvedBy.isBlank()) {
            throw new IllegalArgumentException("approvedBy must not be blank: an approved exception needs "
                    + "an owner");
        }
    }
}
