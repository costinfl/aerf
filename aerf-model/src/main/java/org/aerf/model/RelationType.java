package org.aerf.model;

/**
 * Edge relation type, tau_E in AERF v0.4 section 2.4.
 */
public enum RelationType {
    CALL,
    DEPENDS,
    READS,
    WRITES,
    EXTENDS,
    IMPLEMENTS,
    RENDERS,
    CONFIGURES,
    COMMUNICATES
}
