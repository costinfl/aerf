package org.aerf.model;

/**
 * Architectural role, rho(v) in AERF v0.4 section 2.3. Assigned by role
 * inference (not implemented in this increment), independent of node type
 * and independent of technology/framework annotations.
 *
 * <p>{@link #UNKNOWN} is a valid, expected outcome (section 3.5) and must
 * never be silently coerced into a more specific role for lack of
 * evidence.
 */
public enum Role {
    PRESENTATION,
    APPLICATION,
    DOMAIN,
    PERSISTENCE,
    INFRASTRUCTURE,
    EXTERNAL,
    UNKNOWN
}
