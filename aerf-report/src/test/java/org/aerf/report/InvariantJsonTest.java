package org.aerf.report;

import org.aerf.analysis.invariant.InvariantEvaluationResult;
import org.aerf.analysis.invariant.InvariantEvaluator;
import org.aerf.analysis.invariant.examples.SpecWorkedExamples;
import org.aerf.model.Graph;
import org.aerf.model.fixtures.CanonicalSampleGraphs;
import org.aerf.report.json.JsonWriter;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InvariantJsonTest {

    @Test
    void theFixtureGraphsViolationSerializesWithFullEdgeEvidence() {
        Graph graph = CanonicalSampleGraphs.layeredOrderSlice();
        InvariantEvaluationResult result = new InvariantEvaluator()
                .evaluate(SpecWorkedExamples.noPresentationToPersistence(), graph, Map.of());

        String json = JsonWriter.write(InvariantJson.evaluationResult(result));

        assertTrue(json.contains("\"invariantName\":\"no_presentation_to_persistence\""));
        assertTrue(json.contains("\"severity\":\"critical\""));
        assertTrue(json.contains("\"holds\":false"));
        assertTrue(json.contains("\"indicatorValue\":1"));
        assertTrue(json.contains("\"kind\":\"edge\""));
        assertTrue(json.contains(CanonicalSampleGraphs.ORDER_CONTROLLER.value()));
        assertTrue(json.contains(CanonicalSampleGraphs.ORDER_REPOSITORY.value()));
    }

    @Test
    void aHoldingInvariantSerializesAnEmptyViolationsArray() {
        InvariantEvaluationResult result = new InvariantEvaluator()
                .evaluate(SpecWorkedExamples.entropyBudget(0.35), Graph.builder().build(), Map.of("total_entropy", 0.1));

        String json = JsonWriter.write(InvariantJson.evaluationResult(result));

        assertTrue(json.contains("\"holds\":true"));
        assertTrue(json.contains("\"violations\":[]"));
    }
}
