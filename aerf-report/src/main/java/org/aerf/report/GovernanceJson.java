package org.aerf.report;

import org.aerf.analysis.calibration.WeightedDimension;
import org.aerf.analysis.governance.GovernancePolicy;
import org.aerf.analysis.governance.Subsystem;
import org.aerf.analysis.invariant.Invariant;
import org.aerf.analysis.metrics.layer.LayerPolicy;
import org.aerf.model.Role;
import org.aerf.report.json.JsonObjectBuilder;
import org.aerf.report.json.JsonValue;

import java.util.Map;
import java.util.Set;

/**
 * Serializes the {@code GovernancePolicy} a run was measured under
 * (Increment 25, OQ-02), so a report says not only what was measured but
 * what it was measured <em>against</em>. Without this, two scans of the
 * same project could differ entirely because the layering matrix or the
 * calibration weights changed, and nothing in either report would say so.
 *
 * <p>Ordering is canonical throughout — roles in {@link Role} declaration
 * order (guaranteed by {@code LayerPolicy} itself), dimensions and
 * invariants in the order they were declared — so identical governance
 * serializes to identical bytes across runs, as section 14 requires.
 *
 * <p><b>One part of a governance declaration is not serialized here: an
 * invariant's predicates.</b> Only its name, scope, severity and the
 * metric names it references are emitted. There is no {@code Predicate}
 * serializer anywhere in this project and inventing one would be
 * designing the textual form of the invariant DSL, which AERF v0.4
 * deliberately leaves unimplemented ({@code InvariantDsl}'s own javadoc
 * says so, and {@code InvariantJson} has stayed results-only for the
 * same reason). So this output is <em>explicitly partial</em>: a reader
 * can see which invariants governed a run and how severely, but not the
 * conditions they assert. It is recorded as partial rather than
 * presented as complete — the same reason a governance hash was rejected
 * for this increment, since a digest over incomplete input would claim
 * two policies identical when only their names matched.
 */
public final class GovernanceJson {

    private GovernanceJson() {
    }

    public static JsonValue policy(GovernancePolicy governance) {
        return new JsonObjectBuilder()
                .put("layerPolicy", layerPolicy(governance.layerPolicy()))
                .put("subsystems", JsonSupport.array(
                        governance.subsystems().declared(), GovernanceJson::subsystem))
                .put("includeSelfCyclesInCycleEntropy", governance.includeSelfCyclesInCycleEntropy())
                .put("calibration", JsonSupport.array(
                        governance.calibrationProfile().dimensions(), GovernanceJson::weightedDimension))
                .put("invariants", JsonSupport.array(governance.invariants(), GovernanceJson::invariant))
                .build();
    }

    /**
     * The declared matrix, exactly as declared. A role that is known but
     * was given no allowed targets is absent from {@code allowedTargets}
     * rather than emitted as an empty array — mirroring {@code
     * LayerPolicy}'s own refusal to materialize an entry nobody declared.
     */
    private static JsonValue layerPolicy(LayerPolicy layerPolicy) {
        JsonObjectBuilder allowedTargets = new JsonObjectBuilder();
        for (Map.Entry<Role, Set<Role>> entry : layerPolicy.allowedTargets().entrySet()) {
            allowedTargets.put(entry.getKey().name(), roles(entry.getValue()));
        }
        return new JsonObjectBuilder()
                .put("knownRoles", roles(layerPolicy.knownRoles()))
                .put("allowedTargets", allowedTargets.build())
                .build();
    }

    /**
     * One subsystem's declaration (OQ-04, extended by OQ-06), in the order
     * it was declared. An organization that declared none emits an empty
     * array rather than nothing at all: choosing a single matrix for the
     * whole graph is a governance decision, not the absence of one.
     *
     * <p>{@code layerPolicy} is JSON {@code null} for a subsystem that
     * declared none — it scopes the cycle measurement but is judged by the
     * default matrix, which is a different thing from an empty matrix.
     */
    private static JsonValue subsystem(Subsystem subsystem) {
        return new JsonObjectBuilder()
                .put("name", subsystem.name())
                .put("idPrefix", subsystem.idPrefix())
                .put("layerPolicy", subsystem.layerPolicy()
                        .map(GovernanceJson::layerPolicy)
                        .orElse(JsonValue.JsonNull.INSTANCE))
                .build();
    }

    private static JsonValue roles(Set<Role> roles) {
        return new JsonValue.JsonArray(roles.stream().map(JsonSupport::string).toList());
    }

    /**
     * A dimension weighted {@code 0.0} is serialized like any other, not
     * omitted: weighting security out is a documented governance decision
     * about a current capability boundary, not the absence of one.
     */
    private static JsonValue weightedDimension(WeightedDimension dimension) {
        return new JsonObjectBuilder()
                .put("dimension", dimension.name())
                .put("weight", dimension.weight())
                .put("function", dimension.calibration().name())
                .build();
    }

    private static JsonValue invariant(Invariant invariant) {
        return new JsonObjectBuilder()
                .put("name", invariant.name())
                .put("scope", JsonSupport.string(invariant.scope()))
                .put("severity", invariant.severity())
                .put("referencedMetrics", new JsonValue.JsonArray(
                        invariant.referencedMetricNames().stream()
                                .map(name -> (JsonValue) new JsonValue.JsonString(name))
                                .toList()))
                .build();
    }
}
