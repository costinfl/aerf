package org.aerf.analysis.role;

import org.aerf.model.Role;

import java.util.Objects;

/**
 * A candidate role produced by a single {@link RoleInferenceRule}, kept on
 * a {@link RoleInferenceResult} regardless of whether it won conflict
 * resolution, so a classification remains explainable and auditable
 * (section 3.1, "Explainable": "the engine records evidence and rules
 * leading to a classification").
 */
public record RoleSignal(Role role, String ruleName, String rationale) {
    public RoleSignal {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(ruleName, "ruleName");
        Objects.requireNonNull(rationale, "rationale");
    }
}
