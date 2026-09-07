package org.aerf.model;

/**
 * Structural node type, tau_V in AERF v0.4 section 2.2. Distinct from
 * {@link Role}: type is what an artifact structurally is, role is what it
 * architecturally does.
 */
public enum NodeType {
    MODULE,
    COMPONENT,
    FUNCTION,
    DATA,
    VIEW,
    CONFIG,
    SCRIPT
}
