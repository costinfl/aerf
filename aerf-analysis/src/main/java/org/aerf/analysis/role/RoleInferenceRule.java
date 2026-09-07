package org.aerf.analysis.role;

import org.aerf.model.Node;
import org.aerf.model.Role;

import java.util.Objects;
import java.util.Optional;

/**
 * A single seed-evidence rule, contributing at most one candidate role for
 * a node in isolation. Corresponds to one source of {@code R_seed} in AERF
 * v0.4 section 3.2 ("Seed evidence can come from structural properties,
 * semantic constructs, configuration, naming conventions, and explicit
 * governance rules").
 *
 * <p>A rule must be a pure function of the node it is given: the same node
 * must always produce the same candidate, with no reference to graph
 * structure, external state, or iteration order (section 3.1,
 * "Deterministic").
 */
public interface RoleInferenceRule {

    /** Stable, unique identifier used for explainability and tie-free ordering of signals. */
    String name();

    Optional<Candidate> evaluate(Node node);

    record Candidate(Role role, String rationale) {
        public Candidate {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(rationale, "rationale");
            if (rationale.isBlank()) {
                throw new IllegalArgumentException("rationale must not be blank");
            }
        }
    }
}
