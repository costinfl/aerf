package org.aerf.analysis.invariant;

import org.aerf.analysis.invariant.examples.SpecWorkedExamples;
import org.aerf.model.Graph;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvariantEvaluatorGraphScopeTest {

    private final InvariantEvaluator evaluator = new InvariantEvaluator();
    private final Graph emptyGraph = Graph.builder().build();
    private final Invariant entropyBudget = SpecWorkedExamples.entropyBudget(0.35);

    @Test
    void underBudgetHolds() {
        InvariantEvaluationResult result = evaluator.evaluate(entropyBudget, emptyGraph, Map.of("total_entropy", 0.20));

        assertTrue(result.holds());
    }

    @Test
    void overBudgetViolatesAsASingleGraphLevelFinding() {
        InvariantEvaluationResult result = evaluator.evaluate(entropyBudget, emptyGraph, Map.of("total_entropy", 0.50));

        assertFalse(result.holds());
        assertTrue(result.violations().get(0).subject() instanceof ViolationSubject.OfGraph);
    }

    @Test
    void aMissingMetricThrowsRatherThanSilentlyPassingOrFailing() {
        assertThrows(IllegalStateException.class, () -> evaluator.evaluate(entropyBudget, emptyGraph, Map.of()));
    }
}
