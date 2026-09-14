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
 * <p>What this type deliberately does <em>not</em> carry, each for a
 * recorded reason (see {@code docs/increment-25-*.md}):
 * <ul>
 *   <li><b>Any subsystem scope.</b> {@link #layerPolicy()} is one matrix
 *       for the whole graph and {@link #includeSelfCyclesInCycleEntropy()}
 *       is global. OQ-04 and OQ-06 extend exactly these two components,
 *       additively — but how a subsystem is even identified is OQ-04's
 *       decision, and no such concept exists in the graph model today.
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
        boolean includeSelfCyclesInCycleEntropy,
        CalibrationProfile calibrationProfile,
        List<Invariant> invariants) {

    public GovernancePolicy {
        Objects.requireNonNull(layerPolicy, "layerPolicy");
        Objects.requireNonNull(calibrationProfile, "calibrationProfile");
        invariants = List.copyOf(Objects.requireNonNull(invariants, "invariants"));
    }
}
