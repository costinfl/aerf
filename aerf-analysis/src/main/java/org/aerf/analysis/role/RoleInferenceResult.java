package org.aerf.analysis.role;

import org.aerf.model.NodeId;
import org.aerf.model.Role;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of seed role inference for one node: the winning role and
 * every signal that contributed, whether or not it won precedence.
 */
public record RoleInferenceResult(NodeId nodeId, Role role, List<RoleSignal> signals) {
    public RoleInferenceResult {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(role, "role");
        signals = List.copyOf(Objects.requireNonNull(signals, "signals"));
    }
}
