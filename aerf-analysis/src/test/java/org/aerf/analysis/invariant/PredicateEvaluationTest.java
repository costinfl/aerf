package org.aerf.analysis.invariant;

import org.aerf.model.Graph;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.aerf.analysis.invariant.InvariantDsl.always;
import static org.aerf.analysis.invariant.InvariantDsl.and;
import static org.aerf.analysis.invariant.InvariantDsl.eq;
import static org.aerf.analysis.invariant.InvariantDsl.gt;
import static org.aerf.analysis.invariant.InvariantDsl.in;
import static org.aerf.analysis.invariant.InvariantDsl.lt;
import static org.aerf.analysis.invariant.InvariantDsl.not;
import static org.aerf.analysis.invariant.InvariantDsl.or;
import static org.aerf.analysis.invariant.InvariantDsl.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises pure logical/comparison/membership correctness via
 * GRAPH-scope invariants built entirely from {@link ValueExpression.Constant}s,
 * so no real graph structure is needed - only the operator semantics
 * are under test here.
 */
class PredicateEvaluationTest {

    private final InvariantEvaluator evaluator = new InvariantEvaluator();
    private final Graph emptyGraph = Graph.builder().build();

    private boolean holds(Predicate assertion) {
        Invariant invariant = new Invariant("test", Scope.GRAPH, always(true), assertion, "critical");
        return evaluator.evaluate(invariant, emptyGraph, Map.of()).holds();
    }

    @Test
    void andRequiresAllOperands() {
        assertTrue(holds(and(always(true), always(true))));
        assertFalse(holds(and(always(true), always(false))));
    }

    @Test
    void orRequiresAnyOperand() {
        assertTrue(holds(or(always(false), always(true))));
        assertFalse(holds(or(always(false), always(false))));
    }

    @Test
    void notInvertsItsOperand() {
        assertTrue(holds(not(always(false))));
        assertFalse(holds(not(always(true))));
    }

    @Test
    void equalsComparesResolvedValues() {
        assertTrue(holds(eq(value(1.0), value(1.0))));
        assertFalse(holds(eq(value(1.0), value(2.0))));
    }

    @Test
    void orderingComparisonsWorkOnNumbers() {
        assertTrue(holds(lt(value(0.2), value(0.35))));
        assertTrue(holds(gt(value(0.5), value(0.35))));
        assertFalse(holds(lt(value(0.5), value(0.35))));
    }

    @Test
    void orderingComparisonOnNonNumericValuesThrows() {
        Invariant invariant = new Invariant("test", Scope.GRAPH, always(true), lt(value("a"), value("b")), "critical");

        assertThrows(IllegalStateException.class, () -> evaluator.evaluate(invariant, emptyGraph, Map.of()));
    }

    @Test
    void membershipMatchesAnyCandidate() {
        assertTrue(holds(in(value("x"), value("a"), value("x"), value("z"))));
        assertFalse(holds(in(value("y"), value("a"), value("x"), value("z"))));
    }

    @Test
    void combinedExpressionEvaluatesCorrectly() {
        // not(1 == 2 and 3 < 4) == not(false and true) == not(false) == true
        Predicate expression = not(and(eq(value(1.0), value(2.0)), lt(value(3.0), value(4.0))));

        assertTrue(holds(expression));
    }

    @Test
    void indicatorValueMatchesSection61Definition() {
        Invariant holdsInvariant = new Invariant("holds", Scope.GRAPH, always(true), always(true), "critical");
        Invariant violatesInvariant = new Invariant("violates", Scope.GRAPH, always(true), always(false), "critical");

        assertEquals(0, evaluator.evaluate(holdsInvariant, emptyGraph, Map.of()).indicatorValue());
        assertEquals(1, evaluator.evaluate(violatesInvariant, emptyGraph, Map.of()).indicatorValue());
    }
}
