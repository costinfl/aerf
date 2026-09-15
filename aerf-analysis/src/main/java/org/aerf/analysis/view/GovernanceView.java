package org.aerf.analysis.view;

import org.aerf.analysis.calibration.InvariantAggregate;
import org.aerf.analysis.calibration.MaturityLevel;
import org.aerf.analysis.governance.ApprovedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The unified governance-facing view (Increment 31, OQ-16): everything
 * the preceding increments measured, composed into one value that answers
 * the five questions the backlog commissions.
 *
 * <ol>
 *   <li><b>What architectural condition was observed?</b>
 *       {@link #observed()} per dimension, with {@link #totalEntropy()},
 *       {@link #maturity()}, {@link #maturityLevel()} and
 *       {@link #confidence()}.
 *   <li><b>What changed relative to baseline?</b>
 *       {@link #comparison()}'s drift, when a caller supplied a baseline.
 *   <li><b>What governance constraints were violated?</b>
 *       {@link #violatedConstraints()} and {@link #invariantAggregate()}.
 *   <li><b>What risk interpretation follows?</b>
 *       {@link #comparison()}'s {@code RiskAssessment}.
 *   <li><b>What evidence supports each conclusion?</b>
 *       {@link #findings()}, each naming its subject by identifier — see
 *       {@link TraceableFinding} on why by identifier and not by copy.
 * </ol>
 *
 * <p><b>Composition, not collapse.</b> The commission requires entropy,
 * drift and violations to "remain separately visible rather than
 * collapsing them into a single architecture score", and every field
 * above is one of them kept whole: the entropy dimensions with their own
 * numerators and denominators, drift per dimension with its baseline and
 * current values retained, {@code R} with both its terms and every
 * penalty, {@code E_inv} with every λ_k·I_k term, and each violation with
 * its own subject.
 *
 * <p><b>There is deliberately no verdict.</b> No pass/fail, no status, no
 * ranking, and no overall score beyond the §5.2 {@code maturity} v0.4
 * itself defines. Two reasons, and the second is the stronger one:
 *
 * <ul>
 *   <li>a verdict <em>is</em> the collapse the commission forbids — a
 *       reader who acts on it has stopped reading the parts;
 *   <li>a threshold is an invariant the organization <em>already
 *       declares</em>. §6.3's own worked {@code entropy_budget} example is
 *       exactly that, and it appears in {@link #violatedConstraints()}
 *       like any other. A verdict computed here would be a second,
 *       hard-coded threshold competing with the declared one.
 * </ul>
 *
 * <p>Deriving a verdict from {@code severity} was refused for a third
 * reason already recorded in Increment 29: {@code Invariant}'s javadoc
 * keeps severity free text so that no taxonomy v0.4 declines to state is
 * asserted, and reading it as "blocking" would assert one.
 *
 * <p><b>This view alters nothing it reports.</b> It is derived strictly
 * downstream of measurement — no calculator is involved, no denominator
 * can move, and {@link #invariantAggregate()} is the aggregate as
 * computed, never renormalized. That last point is a standing open
 * question, not an oversight: §6.1 imposes no normalization on λ_k, so
 * {@code E_inv} remains unbounded here, and bounding it to make this view
 * tidier would be inventing what the specification declines to state.
 *
 * <p>Note also where this class lives. Increment 25 drew an authorship
 * boundary in which everything under {@code org.aerf.analysis.governance}
 * is something the organization <em>declares</em>. This view is
 * <em>derived</em>, so it sits outside that package rather than blurring
 * the line that increment established.
 *
 * @param subjectId             what was measured, supplied by the caller — this project
 *                              has no concept of project identity of its own
 * @param governanceFingerprint which policy produced these numbers, from Increment 30
 * @param observed              the four §4 dimensions, in the pipeline's own order
 * @param comparison            the baseline-relative half, absent when no baseline was given
 * @param findings              every §4 finding, excused ones included and marked
 * @param violatedConstraints   every configured invariant, held ones included
 * @param invariantAggregate    §6.1's {@code E_inv} exactly as computed
 * @param unmatchedExceptions   declared exceptions that matched no finding this run
 */
public record GovernanceView(
        String subjectId,
        Optional<String> governanceFingerprint,
        List<DimensionObservation> observed,
        OptionalDouble totalEntropy,
        OptionalDouble maturity,
        Optional<MaturityLevel> maturityLevel,
        OptionalDouble confidence,
        Optional<BaselineComparison> comparison,
        List<TraceableFinding> findings,
        List<ViolatedConstraint> violatedConstraints,
        InvariantAggregate invariantAggregate,
        List<ApprovedException> unmatchedExceptions) {

    public GovernanceView {
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(governanceFingerprint, "governanceFingerprint");
        Objects.requireNonNull(totalEntropy, "totalEntropy");
        Objects.requireNonNull(maturity, "maturity");
        Objects.requireNonNull(maturityLevel, "maturityLevel");
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(comparison, "comparison");
        Objects.requireNonNull(invariantAggregate, "invariantAggregate");
        if (subjectId.isBlank()) {
            throw new IllegalArgumentException("subjectId must not be blank");
        }
        observed = List.copyOf(Objects.requireNonNull(observed, "observed"));
        findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
        violatedConstraints = List.copyOf(Objects.requireNonNull(violatedConstraints, "violatedConstraints"));
        unmatchedExceptions = List.copyOf(Objects.requireNonNull(unmatchedExceptions, "unmatchedExceptions"));
    }

    /**
     * Which of the five commissioned questions this view could not
     * answer, and why — derived, so it cannot disagree with the contents
     * above.
     *
     * <p>It exists because an empty section is ambiguous. Without a
     * baseline, a drift map with no entries and a system that genuinely
     * did not move serialize identically, and a governance reader would
     * have no way to tell "nothing changed" from "nothing was compared".
     * Saying so explicitly is the idiom this project already uses for
     * {@code skippedInvariants}, {@code unevaluatedWeightedInvariants} and
     * {@code RiskAssessment.undefinedBecause}.
     *
     * <p>Questions 1 and 3 are always answered: the observations and the
     * configured constraints exist whatever the run found, and an
     * undefined dimension is itself an answer about the architectural
     * condition rather than a failure to give one.
     */
    public List<String> unanswered() {
        List<String> gaps = new ArrayList<>();
        if (comparison.isEmpty()) {
            gaps.add("what changed relative to baseline: no baseline was supplied, so nothing was "
                    + "compared - this is not a statement that nothing changed");
            gaps.add("what risk interpretation follows: R is drift-aware and needs a baseline");
        } else {
            for (String reason : comparison.get().risk().undefinedBecause()) {
                gaps.add("what risk interpretation follows: " + reason);
            }
        }
        long unaddressable = findings.stream().filter(finding -> finding.subject().isEmpty()).count();
        if (unaddressable > 0) {
            gaps.add("what evidence supports each conclusion: " + unaddressable + " finding(s) resolved "
                    + "to no node or edge identifier, so they can be read in their own dimension's "
                    + "result but not addressed individually");
        }
        return List.copyOf(gaps);
    }

    /**
     * Findings the organization has not already accepted.
     *
     * <p>A reading of {@link #findings()}, never a filter applied to it.
     * Increment 28 decided that an approved exception accepts a finding
     * rather than denying it, and nothing here removes one or moves a
     * count: every {@link DimensionObservation} above still reports what
     * was measured.
     */
    public List<TraceableFinding> unexcusedFindings() {
        return findings.stream().filter(finding -> !finding.isExcused()).toList();
    }

    /** The named dimension's observation, or empty if this view has none. */
    public Optional<DimensionObservation> observationOf(String dimension) {
        Objects.requireNonNull(dimension, "dimension");
        return observed.stream().filter(o -> o.dimension().equals(dimension)).findFirst();
    }
}
