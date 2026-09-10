package org.aerf.model;

/**
 * Edge relation type, tau_E in AERF v0.4 section 2.4.
 *
 * <p>{@link #MEMBER_OF} was added by AERF v0.4.1 patch Amendment 6
 * (open question #17): a purely structural relation from a
 * {@code FUNCTION} node to the {@code COMPONENT} node that declares it,
 * with no bearing on role inference by itself (see the amendment for
 * why graph-relationship role refinement does not consume it).
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
    COMMUNICATES,
    MEMBER_OF
}
