package org.aerf.analysis.governance;

import org.aerf.analysis.calibration.CalibrationProfile;
import org.aerf.analysis.invariant.Invariant;
import org.aerf.analysis.metrics.layer.LayerPolicy;

import java.util.List;
import java.util.Objects;

/**
 * Everything an <em>organization</em> declares about how its architecture
 * is to be judged — AERF v0.4's governance inputs, gathered into one
 * named value (Increment 25, OQ-02).
 *
 * <p>Before this type existed these four inputs sat interleaved with
 * engineering inputs and technology heuristics in {@code PipelineConfig},
 * with nothing naming which was which. The grouping is not a judgement
 * call: each member already describes itself as governance in its own
 * documentation — {@link LayerPolicy} is "a governance-declared layering
 * matrix", {@link CalibrationProfile} "a validated governance
 * configuration for aggregation", {@link Invariant} is section 6's
 * governance rule, and section 4.2 says trivial single-node SCCs are
 * "excluded unless self-cycles are <em>explicitly governed</em>", which
 * is exactly {@link #includeSelfCyclesInCycleEntropy()}.
 *
 * <p><b>Nothing here has a default.</b> Every component is required, so
 * a policy cannot be built by omission and no governance choice can be
 * made silently on an organization's behalf — OQ-02's "no hidden default
 * governance policy is introduced", enforced by the compiler rather than
 * by documentation. An organization that wants no invariants says so with
 * an empty list.
 *
 * <p>{@link #layerPolicy()} is the <b>default</b> layering matrix: the
 * one governing any node no declared subsystem claims. Increment 26
 * (OQ-04) added {@link #subsystems()} beside it, so an organization can
 * give a subsystem that evolved in a different era its own matrix.
 * Declaring none leaves the default governing everything, which is
 * exactly the behaviour that existed before — on the same code path, not
 * a parallel one.
 *
 * <p>{@link #invariantWeights()} (Increment 29, OQ-15) carries §6.1's
 * {@code lambda_k}, the importance an organization attaches to each
 * invariant, and produces {@code E_inv} beside the measurements.
 * Declaring none leaves {@code E_inv} undefined rather than zero. The
 * weight sits here rather than on {@link Invariant} for the same reason
 * an entropy dimension's weight sits in {@code CalibrationProfile}: it is
 * a governance choice about a rule, not a property of one.
 *
 * <p>{@link #driftSensitivity()} (Increment 30, OQ-14) carries §5.3's
 * {@code beta} and {@code gamma_d} — how much an organization cares about
 * architecture getting <em>worse</em>, as distinct from how bad it is now.
 * Declaring none leaves {@code R} undefined rather than silently equal to
 * {@code E_total}.
 *
 * <p>{@link #approvedExceptions()} (Increment 28, OQ-09) records the
 * findings an organization has looked at and decided to live with. It
 * changes no measured value and removes no finding: it produces an
 * {@code ExceptionLedger} beside the measurements, saying which findings
 * are already accepted and by whom. See {@link ApprovedException} for why
 * acceptance must not move a number.
 *
 * <p>{@link #subsystems()} serves cycle entropy too since Increment 27
 * (OQ-06), which reports a value per declared subsystem alongside the
 * unchanged global one. One declaration, two scoped dimensions — so a
 * node's subsystem never depends on which metric is asking.
 *
 * <p>What this type deliberately does <em>not</em> carry, each for a
 * recorded reason (see {@code docs/increment-25-*.md} and
 * {@code docs/increment-26-*.md}):
 * <ul>
 *   <li><b>Any cycle-tolerance policy.</b> Governance can scope the
 *       cycle <em>measurement</em> (Increment 27), but cannot declare a
 *       cycle relevant or not — e.g. tolerating one confined to a single
 *       subsystem. That would change section 4.2's own definition of
 *       relevance rather than extend governance configuration, and no
 *       repository in this project's evidence base contains a cycle at
 *       all to calibrate such a policy against.
 *       {@link #includeSelfCyclesInCycleEntropy()} remains section 4.2's
 *       one named lever on cycle relevance, and stays global.
 *   <li><b>Any role assignment.</b> Section 3.3's fourth evidence class,
 *       Governance — an organization declaring a node's role outright —
 *       remains unimplemented. That is the other half of OQ-02 and stays
 *       open; see the increment doc for why it cannot simply be another
 *       {@code RoleInferenceRule}.
 *   <li><b>Which relations each dimension measures over.</b> That is
 *       measurement definition fixed by section 4, not an organizational
 *       input.
 * </ul>
 */
public record GovernancePolicy(
        LayerPolicy layerPolicy,
        Subsystems subsystems,
        boolean includeSelfCyclesInCycleEntropy,
        CalibrationProfile calibrationProfile,
        List<Invariant> invariants,
        InvariantWeights invariantWeights,
        DriftSensitivity driftSensitivity,
        ApprovedExceptions approvedExceptions) {

    public GovernancePolicy {
        Objects.requireNonNull(layerPolicy, "layerPolicy");
        Objects.requireNonNull(subsystems, "subsystems");
        Objects.requireNonNull(calibrationProfile, "calibrationProfile");
        invariants = List.copyOf(Objects.requireNonNull(invariants, "invariants"));
        Objects.requireNonNull(invariantWeights, "invariantWeights");
        Objects.requireNonNull(driftSensitivity, "driftSensitivity");
        Objects.requireNonNull(approvedExceptions, "approvedExceptions");
    }

    /**
     * The common case: one layering matrix for the whole graph, no
     * subsystem declared. Identical to passing
     * {@link Subsystems#none()}, and named so that declaring
     * no subsystems reads as the decision it is.
     */
    public static GovernancePolicy withOneLayerMatrix(
            LayerPolicy layerPolicy,
            boolean includeSelfCyclesInCycleEntropy,
            CalibrationProfile calibrationProfile,
            List<Invariant> invariants) {
        return new GovernancePolicy(layerPolicy, Subsystems.none(),
                includeSelfCyclesInCycleEntropy, calibrationProfile, invariants,
                InvariantWeights.none(), DriftSensitivity.none(), ApprovedExceptions.none());
    }
}
