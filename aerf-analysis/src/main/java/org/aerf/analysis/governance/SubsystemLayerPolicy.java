package org.aerf.analysis.governance;

import org.aerf.analysis.metrics.layer.LayerPolicy;
import org.aerf.model.NodeId;

import java.util.Objects;

/**
 * One subsystem's own layering matrix (Increment 26, OQ-04): "a legacy
 * system might reasonably apply different layering rules to different
 * subsystems that evolved in different eras."
 *
 * <p><b>A subsystem is governance-declared, not source-derived.</b> The
 * extractor does not know what an organization's subsystems are; its
 * architects do. That is why this lives beside the other governance
 * declarations rather than in the graph model, and why OQ-04 needed no
 * model change, no new relation and no extraction change at all.
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
 */
public record SubsystemLayerPolicy(String name, String idPrefix, LayerPolicy layerPolicy) {

    public SubsystemLayerPolicy {
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

    /** Whether this subsystem claims the given node. */
    public boolean matches(NodeId id) {
        return Objects.requireNonNull(id, "id").value().startsWith(idPrefix);
    }
}
