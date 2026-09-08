package org.aerf.analysis.invariant;

import java.util.List;
import java.util.Objects;

/**
 * A boolean expression over {@link ValueExpression}s. Deliberately closed
 * and non-recursive-beyond-composition: there is no variable binding, no
 * function call, no loop — exactly the "non-Turing-complete and bounded
 * semantics" AERF v0.4 section 6.2 requires. Comparison and membership
 * operations, plus AND/OR/NOT, are the complete operator set section 6.2
 * asks for.
 */
public sealed interface Predicate {

    record Equals(ValueExpression left, ValueExpression right) implements Predicate {
        public Equals {
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(right, "right");
        }
    }

    record NotEquals(ValueExpression left, ValueExpression right) implements Predicate {
        public NotEquals {
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(right, "right");
        }
    }

    record LessThan(ValueExpression left, ValueExpression right) implements Predicate {
        public LessThan {
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(right, "right");
        }
    }

    record LessThanOrEqual(ValueExpression left, ValueExpression right) implements Predicate {
        public LessThanOrEqual {
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(right, "right");
        }
    }

    record GreaterThan(ValueExpression left, ValueExpression right) implements Predicate {
        public GreaterThan {
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(right, "right");
        }
    }

    record GreaterThanOrEqual(ValueExpression left, ValueExpression right) implements Predicate {
        public GreaterThanOrEqual {
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(right, "right");
        }
    }

    /** Membership: true if {@code value} equals any of {@code candidates}. */
    record In(ValueExpression value, List<ValueExpression> candidates) implements Predicate {
        public In {
            Objects.requireNonNull(value, "value");
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        }
    }

    record And(List<Predicate> operands) implements Predicate {
        public And {
            operands = List.copyOf(Objects.requireNonNull(operands, "operands"));
            if (operands.isEmpty()) {
                throw new IllegalArgumentException("And requires at least one operand");
            }
        }
    }

    record Or(List<Predicate> operands) implements Predicate {
        public Or {
            operands = List.copyOf(Objects.requireNonNull(operands, "operands"));
            if (operands.isEmpty()) {
                throw new IllegalArgumentException("Or requires at least one operand");
            }
        }
    }

    record Not(Predicate operand) implements Predicate {
        public Not {
            Objects.requireNonNull(operand, "operand");
        }
    }

    /** A constant predicate, e.g. section 6.4's {@code when: true}. */
    record Always(boolean value) implements Predicate {
    }
}
