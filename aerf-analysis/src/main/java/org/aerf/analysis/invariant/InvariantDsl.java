package org.aerf.analysis.invariant;

import java.util.List;

/**
 * Small factory helpers for building {@link Predicate}/{@link ValueExpression}
 * trees programmatically, so an {@link Invariant} can be constructed in
 * Java that reads close to AERF v0.4 section 6.3's own pseudocode syntax.
 *
 * <p>This is not a parser: there is no textual invariant syntax yet.
 * Section 6.3 states "YAML may be used as a transport format while the
 * semantic model remains independent of YAML" — the semantic model (this
 * package) comes first and is deliberately decoupled from any concrete
 * syntax; a parser translating a textual/YAML representation into this
 * same {@link Predicate}/{@link Invariant} model is a separate, later
 * increment.
 */
public final class InvariantDsl {

    private InvariantDsl() {
    }

    public static ValueExpression.Property sourceRole() {
        return ValueExpression.Property.of(PropertyKey.SOURCE_ROLE);
    }

    public static ValueExpression.Property sourceType() {
        return ValueExpression.Property.of(PropertyKey.SOURCE_TYPE);
    }

    public static ValueExpression.Property targetRole() {
        return ValueExpression.Property.of(PropertyKey.TARGET_ROLE);
    }

    public static ValueExpression.Property targetType() {
        return ValueExpression.Property.of(PropertyKey.TARGET_TYPE);
    }

    public static ValueExpression.Property edgeRelation() {
        return ValueExpression.Property.of(PropertyKey.EDGE_RELATION);
    }

    public static ValueExpression.Property nodeRole() {
        return ValueExpression.Property.of(PropertyKey.NODE_ROLE);
    }

    public static ValueExpression.Property nodeType() {
        return ValueExpression.Property.of(PropertyKey.NODE_TYPE);
    }

    public static ValueExpression.Property metric(String name) {
        return ValueExpression.Property.metric(name);
    }

    public static ValueExpression.Constant value(Object literal) {
        return new ValueExpression.Constant(literal);
    }

    public static Predicate eq(ValueExpression left, ValueExpression right) {
        return new Predicate.Equals(left, right);
    }

    public static Predicate notEq(ValueExpression left, ValueExpression right) {
        return new Predicate.NotEquals(left, right);
    }

    public static Predicate lt(ValueExpression left, ValueExpression right) {
        return new Predicate.LessThan(left, right);
    }

    public static Predicate lte(ValueExpression left, ValueExpression right) {
        return new Predicate.LessThanOrEqual(left, right);
    }

    public static Predicate gt(ValueExpression left, ValueExpression right) {
        return new Predicate.GreaterThan(left, right);
    }

    public static Predicate gte(ValueExpression left, ValueExpression right) {
        return new Predicate.GreaterThanOrEqual(left, right);
    }

    public static Predicate in(ValueExpression value, ValueExpression... candidates) {
        return new Predicate.In(value, List.of(candidates));
    }

    public static Predicate and(Predicate... operands) {
        return new Predicate.And(List.of(operands));
    }

    public static Predicate or(Predicate... operands) {
        return new Predicate.Or(List.of(operands));
    }

    public static Predicate not(Predicate operand) {
        return new Predicate.Not(operand);
    }

    public static Predicate always(boolean value) {
        return new Predicate.Always(value);
    }
}
