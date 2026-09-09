package org.aerf.analysis.invariant;

import org.aerf.analysis.invariant.examples.SpecWorkedExamples;
import org.aerf.model.Role;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.aerf.analysis.invariant.InvariantDsl.always;
import static org.aerf.analysis.invariant.InvariantDsl.and;
import static org.aerf.analysis.invariant.InvariantDsl.eq;
import static org.aerf.analysis.invariant.InvariantDsl.gt;
import static org.aerf.analysis.invariant.InvariantDsl.metric;
import static org.aerf.analysis.invariant.InvariantDsl.nodeRole;
import static org.aerf.analysis.invariant.InvariantDsl.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvariantTest {

    @Test
    void aGraphScopeInvariantReferencingOneMetricReportsExactlyThatName() {
        Invariant invariant = SpecWorkedExamples.entropyBudget(0.35);

        assertEquals(Set.of("total_entropy"), invariant.referencedMetricNames());
    }

    @Test
    void anInvariantReferencingNoMetricReportsAnEmptySet() {
        Invariant invariant = SpecWorkedExamples.noPresentationToPersistence();

        assertTrue(invariant.referencedMetricNames().isEmpty());
    }

    @Test
    void metricNamesInsideAndOrNotAreAllCollected() {
        Invariant invariant = new Invariant(
                "combined",
                Scope.GRAPH,
                always(true),
                and(gt(metric("layer"), value(0.0)), eq(metric("cycle"), value(0.0))),
                "critical");

        assertEquals(Set.of("layer", "cycle"), invariant.referencedMetricNames());
    }

    @Test
    void aNodeScopePropertyIsNeverReportedAsAMetricName() {
        Invariant invariant = new Invariant(
                "node-only",
                Scope.NODE,
                always(true),
                eq(nodeRole(), value(Role.PRESENTATION)),
                "critical");

        assertTrue(invariant.referencedMetricNames().isEmpty());
    }
}
