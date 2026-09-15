package org.aerf.analysis.governance;

import org.aerf.analysis.calibration.WeightedDimension;
import org.aerf.analysis.invariant.Invariant;
import org.aerf.analysis.metrics.layer.LayerPolicy;
import org.aerf.model.Role;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/**
 * A stable digest of everything readable in a {@link GovernancePolicy}
 * (Increment 30, OQ-14), so a stored measurement can record which policy
 * produced it.
 *
 * <p><b>Its guarantee is one-directional, and that is the whole point of
 * reading this javadoc.</b>
 *
 * <ul>
 *   <li><b>Different fingerprints prove the policies differ.</b> Sound,
 *       and this is the only direction anything relies on.
 *   <li><b>Identical fingerprints do <em>not</em> prove the policies are
 *       the same.</b> An invariant's {@code when}/{@code assert}
 *       predicates are not serializable — no {@code Predicate} writer
 *       exists, because that is the deliberately-unimplemented DSL work —
 *       so two policies differing only in what an invariant actually
 *       asserts fingerprint identically.
 * </ul>
 *
 * <p>Increment 25 rejected a governance fingerprint, and this does not
 * overturn that: it was rejected as a <em>substitute</em> for serializing
 * governance, because "a digest over incomplete input would claim two
 * policies identical when only their names matched". That objection is
 * about the sameness direction, and it still stands. Here the full policy
 * is serialized as it has been since increment 25, and this digest sits
 * beside it as a change <em>detector</em>, used only where it is sound.
 *
 * <p>Covered: the layer matrix, declared subsystems and their matrices,
 * the self-cycle choice, calibration weights and function names, each
 * invariant's name/scope/severity/referenced metrics, λ_k, and every
 * approved exception. Not covered: invariant predicates, and
 * {@code CalibrationFunction} identity beyond its {@code name()}.
 */
public final class GovernanceFingerprint {

    private GovernanceFingerprint() {
    }

    /**
     * A hex SHA-256 over a canonical rendering of the policy. Canonical
     * means order-independent where the policy's own order is not
     * meaningful and order-preserving where it is — {@code LayerPolicy}
     * already stores roles in {@code Role} declaration order, and
     * declaration lists are rendered in the order the organization wrote
     * them, so identical declarations always render identically.
     */
    public static String of(GovernancePolicy governance) {
        Objects.requireNonNull(governance, "governance");
        return sha256(canonicalForm(governance));
    }

    static String canonicalForm(GovernancePolicy governance) {
        StringJoiner form = new StringJoiner("\n");
        form.add("layerPolicy=" + layerPolicy(governance.layerPolicy()));

        StringJoiner subsystems = new StringJoiner(",", "[", "]");
        for (Subsystem subsystem : governance.subsystems().declared()) {
            subsystems.add(subsystem.name() + "@" + subsystem.idPrefix() + "->"
                    + subsystem.layerPolicy().map(GovernanceFingerprint::layerPolicy).orElse("default"));
        }
        form.add("subsystems=" + subsystems);
        form.add("includeSelfCyclesInCycleEntropy=" + governance.includeSelfCyclesInCycleEntropy());

        StringJoiner calibration = new StringJoiner(",", "[", "]");
        for (WeightedDimension dimension : governance.calibrationProfile().dimensions()) {
            calibration.add(dimension.name() + "=" + dimension.weight() + ":" + dimension.calibration().name());
        }
        form.add("calibration=" + calibration);

        StringJoiner invariants = new StringJoiner(",", "[", "]");
        for (Invariant invariant : governance.invariants()) {
            // Name, scope, severity and referenced metrics only: an
            // invariant's predicates cannot be rendered, which is exactly
            // the limit this class's javadoc states.
            invariants.add(invariant.name() + ":" + invariant.scope() + ":" + invariant.severity()
                    + ":" + invariant.referencedMetricNames());
        }
        form.add("invariants=" + invariants);

        StringJoiner weights = new StringJoiner(",", "[", "]");
        for (WeightedInvariant weighted : governance.invariantWeights().declared()) {
            weights.add(weighted.invariantName() + "=" + weighted.weight());
        }
        form.add("invariantWeights=" + weights);

        StringJoiner sensitivity = new StringJoiner(",", "[", "]");
        for (WeightedDriftDimension weighted : governance.driftSensitivity().declared()) {
            sensitivity.add(weighted.dimension() + "=" + weighted.weight());
        }
        form.add("driftSensitivity=beta:" + governance.driftSensitivity().beta() + sensitivity);

        StringJoiner exceptions = new StringJoiner(",", "[", "]");
        for (ApprovedException exception : governance.approvedExceptions().declared()) {
            exceptions.add(exception.target() + "/" + exception.reason() + "/" + exception.approvedBy());
        }
        form.add("approvedExceptions=" + exceptions);

        return form.toString();
    }

    private static String layerPolicy(LayerPolicy policy) {
        StringJoiner rendered = new StringJoiner(";", "{", "}");
        rendered.add("known=" + policy.knownRoles());
        for (Map.Entry<Role, Set<Role>> entry : policy.allowedTargets().entrySet()) {
            rendered.add(entry.getKey() + "->" + entry.getValue());
        }
        return rendered.toString();
    }

    private static String sha256(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every Java platform", e);
        }
    }
}
