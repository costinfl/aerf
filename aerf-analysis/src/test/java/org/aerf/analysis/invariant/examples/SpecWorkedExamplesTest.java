package org.aerf.analysis.invariant.examples;

import org.aerf.analysis.invariant.Invariant;
import org.aerf.analysis.invariant.Scope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpecWorkedExamplesTest {

    @Test
    void noPresentationToPersistenceMatchesSection63() {
        Invariant invariant = SpecWorkedExamples.noPresentationToPersistence();

        assertEquals("no_presentation_to_persistence", invariant.name());
        assertEquals(Scope.EDGE, invariant.scope());
        assertEquals("critical", invariant.severity());
    }

    @Test
    void entropyBudgetMatchesSection64() {
        Invariant invariant = SpecWorkedExamples.entropyBudget(0.35);

        assertEquals("entropy_budget", invariant.name());
        assertEquals(Scope.GRAPH, invariant.scope());
        assertEquals("critical", invariant.severity());
    }
}
