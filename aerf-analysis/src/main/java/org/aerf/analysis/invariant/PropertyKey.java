package org.aerf.analysis.invariant;

/**
 * The closed, bounded vocabulary of properties an invariant may
 * reference (AERF v0.4 section 6.2: "References to canonical node,
 * edge, role, and metric properties"). Each key is valid only in one
 * {@link Scope}; {@link InvariantEvaluator} rejects a property used in
 * the wrong scope rather than resolving it to a meaningless value.
 */
public enum PropertyKey {
    /** NODE scope. */
    NODE_ROLE,
    /** NODE scope. */
    NODE_TYPE,
    /** EDGE scope. */
    SOURCE_ROLE,
    /** EDGE scope. */
    SOURCE_TYPE,
    /** EDGE scope. */
    TARGET_ROLE,
    /** EDGE scope. */
    TARGET_TYPE,
    /** EDGE scope. */
    EDGE_RELATION,
    /** GRAPH scope; requires a metric name, see {@link ValueExpression.Property#metricName()}. */
    METRIC
}
