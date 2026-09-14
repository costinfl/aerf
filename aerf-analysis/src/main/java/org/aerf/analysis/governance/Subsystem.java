package org.aerf.analysis.governance;

import org.aerf.analysis.metrics.layer.LayerPolicy;
import org.aerf.model.NodeId;

import java.util.Objects;
import java.util.Optional;

/**
 * One subsystem an organization has declared: a name, the nodes it
 * claims, and optionally its own layering matrix.
 *
 * <p><b>A subsystem is governance-declared, not source-derived</b>
 * (Increment 26, OQ-04). The extractor does not know what an
 * organization's subsystems are; its architects do. That is why this
 * lives beside the other governance declarations rather than in the
 * graph model, and why subsystem support needed no model change, no new
 * relation and no extraction change at all.
 *
 * <p><b>Membership is a plain prefix test over the node id, and that
 * does not break {@link NodeId}'s opacity.</b> {@code NodeId}'s contract
 * is that <em>AERF</em> does not define or interpret id structure — "an
 * opaque, stable, technology-derived string... AERF does not define how
 * adapters derive it". {@link #matches} honours that literally: it calls
 * {@code String.startsWith}, a total operation on any string, and never
 * parses, splits or interprets anything. The prefix's <em>meaning</em>
 * comes entirely from the governance author, who is entitled to know
 * their own adapter's convention — {@code JavaNodeIds} documents that a
 * Java type's id is its qualified name and a method's is
 * {@code Owner#name(params)}, so declaring
 * {@code com.example.billing} selects that package's types and, because
 * a method id starts with its declaring type's id, their methods too.
 *
 * <p>A selector was chosen over an opaque {@code Predicate} deliberately:
 * a lambda cannot be serialized or read back, which would put a black box
 * inside the {@code governance} report key and contradict the
 * "deterministic and inspectable" criterion Increment 25 established.
 *
 * <p><b>Why the layer matrix is optional</b> (Increment 27, OQ-06).
 * Increment 26 fused subsystem identity with a layering matrix, correctly,
 * because layer entropy was the only consumer. Cycle entropy is now a
 * second consumer and needs the identity without a matrix — so the two
 * are separated, and one declaration list serves both dimensions rather
 * than two lists that could drift apart and put the same node in
 * different subsystems depending on which metric was asking. A subsystem
 * with no matrix of its own is judged by the default one, exactly as an
 * unclaimed node is.
 */
public record Subsystem(String name, String idPrefix, Optional<LayerPolicy> layerPolicy) {

    public Subsystem {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(idPrefix, "idPrefix");
        Objects.requireNonNull(layerPolicy, "layerPolicy");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (idPrefix.isBlank()) {
            // An empty prefix would match every node, silently making this
            // subsystem's matrix the universal one and shadowing the
            // default. An organization wanting that declares it as the
            // default policy instead.
            throw new IllegalArgumentException("idPrefix must not be blank");
        }
    }

    /** A subsystem that scopes measurement but declares no matrix of its own. */
    public static Subsystem of(String name, String idPrefix) {
        return new Subsystem(name, idPrefix, Optional.empty());
    }

    /** A subsystem with its own layering matrix, overriding the default for the nodes it claims. */
    public static Subsystem withLayerPolicy(String name, String idPrefix, LayerPolicy layerPolicy) {
        return new Subsystem(name, idPrefix, Optional.of(Objects.requireNonNull(layerPolicy, "layerPolicy")));
    }

    /** Whether this subsystem claims the given node. */
    public boolean matches(NodeId id) {
        return Objects.requireNonNull(id, "id").value().startsWith(idPrefix);
    }
}
