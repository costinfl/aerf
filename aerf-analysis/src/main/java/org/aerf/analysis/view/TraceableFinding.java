package org.aerf.analysis.view;

import org.aerf.analysis.governance.ApprovedException;
import org.aerf.analysis.governance.ExceptionTarget;

import java.util.Objects;
import java.util.Optional;

/**
 * One finding as the governance view reports it (Increment 31, OQ-16):
 * what was flagged, which matrix judged it, and whether the organization
 * has already accepted it.
 *
 * <p><b>Traceability is by identifier, and that is a contract rather than
 * an omission.</b> This record carries no {@code Evidence}. Every finding's
 * provenance is already serialized in full under the metric that produced
 * it — {@code layerEntropy.violatingEdges}, {@code
 * persistenceEntropy.flaggedEdges}, {@code securityEntropy.flagged}, each
 * edge with its {@code provenance} and each node with its {@code
 * evidence}. Copying it here would roughly double the document to restate
 * what is already in it, and would create two copies that can disagree.
 * So the commission's fifth question — "what evidence supports each
 * conclusion" — is answered by naming the subject precisely enough that
 * the reader resolves it, not by duplicating the answer.
 *
 * <p><b>Why {@link ExceptionTarget} is the subject type.</b> It is
 * already this project's identifier-based addressing for "a finding an
 * approver could name" (Increment 28, OQ-09), and it is exactly the key
 * {@code ExceptionLedger} joins on — so an excusal recorded here can never
 * be matched to a different finding than the one the ledger matched.
 * Introducing a parallel subject type would have meant maintaining that
 * join in two shapes.
 *
 * <p>The subject is {@code Optional} because an edge with an unresolved
 * endpoint is genuinely measured yet names nothing an approver could
 * address — the same gap finding J records for cycles and graph-scope
 * violations. Such a finding is still listed; what it cannot offer is an
 * address.
 *
 * @param dimension  the §4 dimension, or {@code invariant:<name>} for a §6.3 violation,
 *                   matching {@code ExcusedFinding}'s own naming exactly
 * @param subject    the node or edge flagged, empty when nothing addressable was resolved
 * @param governedBy the subsystem whose own layering matrix judged this, empty when the
 *                   default matrix did. Populated for layer findings only — no other
 *                   dimension is judged by a matrix (Increment 31 resolves finding E)
 * @param excusedBy  the approved exception covering this finding, empty when none does.
 *                   An excused finding is still listed: Increment 28 decided that an
 *                   exception accepts a finding rather than denying it
 */
public record TraceableFinding(
        String dimension,
        Optional<ExceptionTarget> subject,
        Optional<String> governedBy,
        Optional<ApprovedException> excusedBy) {

    public TraceableFinding {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(governedBy, "governedBy");
        Objects.requireNonNull(excusedBy, "excusedBy");
        if (dimension.isBlank()) {
            throw new IllegalArgumentException("dimension must not be blank");
        }
    }

    /** Whether the organization has already accepted this finding. */
    public boolean isExcused() {
        return excusedBy.isPresent();
    }
}
