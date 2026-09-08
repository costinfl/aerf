package org.aerf.analysis.role;

import org.aerf.model.NodeId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The outcome of running seed inference followed by graph-relationship
 * refinement to a fixed point. {@code passes} is the number of
 * refinement passes actually needed (0 if seed roles alone already formed
 * a fixed point), kept as evidence of how much the graph-relationship
 * step contributed beyond seeding.
 */
public record IterativeRoleInferenceResult(Map<NodeId, RoleInferenceResult> results, int passes) {

    public IterativeRoleInferenceResult {
        results = Collections.unmodifiableMap(new LinkedHashMap<>(results));
    }
}
