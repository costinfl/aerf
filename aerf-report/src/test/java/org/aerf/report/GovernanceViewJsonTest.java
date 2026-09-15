package org.aerf.report;

import org.aerf.analysis.calibration.DimensionDrift;
import org.aerf.analysis.calibration.DriftPenalty;
import org.aerf.analysis.calibration.InvariantAggregate;
import org.aerf.analysis.calibration.MaturityLevel;
import org.aerf.analysis.calibration.RiskAssessment;
import org.aerf.analysis.governance.ApprovedException;
import org.aerf.analysis.governance.ExceptionTarget;
import org.aerf.analysis.view.BaselineComparison;
import org.aerf.analysis.view.DimensionObservation;
import org.aerf.analysis.view.GovernanceView;
import org.aerf.analysis.view.TraceableFinding;
import org.aerf.analysis.view.ViolatedConstraint;
import org.aerf.model.RelationType;
import org.aerf.report.json.JsonWriter;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Increment 31 (OQ-16): the view serializes as the five questions, separately. */
class GovernanceViewJsonTest {

    private static final ApprovedException EXCEPTION = new ApprovedException(
            new ExceptionTarget.OfNode("com.example.Legacy"), "accepted for the 2026 migration", "alice");

    @Test
    void theFiveQuestionsAreTheDocumentsOwnStructure() {
        String json = JsonWriter.write(GovernanceViewJson.view(full()));

        assertTrue(json.contains("\"observed\":{"), json);
        assertTrue(json.contains("\"changed\":{"), json);
        assertTrue(json.contains("\"violated\":{"), json);
        assertTrue(json.contains("\"risk\":{"), json);
        assertTrue(json.contains("\"findings\":["), json);
    }

    @Test
    void entropyDriftAndViolationsStayInSeparateSectionsRatherThanOneScore() {
        // The commission's binding constraint, made checkable: no key
        // merges them, and each keeps its own parts.
        String json = JsonWriter.write(GovernanceViewJson.view(full()));

        assertTrue(json.contains("\"totalEntropy\":0.5"), json);
        assertTrue(json.contains("\"delta\":0.1"), json);
        assertTrue(json.contains("\"invariantAggregate\":"), json);
        assertTrue(json.contains("\"entropyTerm\":0.5"), json);
        assertTrue(json.contains("\"driftTerm\":0.2"), json);
        assertFalse(json.contains("\"verdict\""), json);
        assertFalse(json.contains("\"score\""), json);
        assertFalse(json.contains("\"status\""), json);
    }

    @Test
    void everyObservationCarriesTheCountsThatProducedItsValue() {
        String json = JsonWriter.write(GovernanceViewJson.view(full()));

        assertTrue(json.contains("\"dimension\":\"layer\""), json);
        assertTrue(json.contains("\"relevantCount\":2.0"), json);
        assertTrue(json.contains("\"findingCount\":1.0"), json);
    }

    @Test
    void aFindingNamesItsSubjectAndItsJudgeAndCarriesNoEvidenceCopy() {
        String json = JsonWriter.write(GovernanceViewJson.view(full()));

        assertTrue(json.contains("\"sourceId\":\"com.example.OrderController\""), json);
        assertTrue(json.contains("\"governedBy\":\"orders\""), json);
        assertFalse(json.contains("\"provenance\""), json);
        assertFalse(json.contains("\"sourceAdapter\""), json);
    }

    @Test
    void anExcusedFindingIsMarkedRatherThanOmitted() {
        String json = JsonWriter.write(GovernanceViewJson.view(full()));

        assertTrue(json.contains("\"excused\":true"), json);
        assertTrue(json.contains("\"approvedBy\":\"alice\""), json);
        assertTrue(json.contains("\"excused\":false"),
                "the unexcused finding is still listed too: " + json);
    }

    @Test
    void withoutABaselineBothBaselineSectionsAreNullNotEmptyObjects() {
        // An empty object would read as "compared, nothing moved", which
        // is a different claim from "not compared".
        String json = JsonWriter.write(GovernanceViewJson.view(withoutBaseline()));

        assertTrue(json.contains("\"changed\":null"), json);
        assertTrue(json.contains("\"risk\":null"), json);
        assertTrue(json.contains("no baseline was supplied"), json);
    }

    @Test
    void anUndefinedRiskSerializesItsReasonsInBothPlaces() {
        GovernanceView view = view(Optional.of(new BaselineComparison(
                Map.of(),
                new RiskAssessment(OptionalDouble.empty(), OptionalDouble.of(0.5), OptionalDouble.empty(),
                        List.of(), List.of("the two measurements were governed by different policies")))));

        String json = JsonWriter.write(GovernanceViewJson.view(view));

        assertTrue(json.contains("\"undefinedBecause\":[\"the two measurements were governed by "
                + "different policies\"]"), json);
        assertTrue(json.contains("\"unanswered\":[\"what risk interpretation follows: the two measurements "
                + "were governed by different policies\"]"), json);
    }

    @Test
    void identicalViewsSerializeIdentically() {
        assertEquals(JsonWriter.write(GovernanceViewJson.view(full())),
                JsonWriter.write(GovernanceViewJson.view(full())));
    }

    private static GovernanceView full() {
        Map<String, DimensionDrift> drift = new LinkedHashMap<>();
        drift.put("layer", new DimensionDrift("layer", 0.4, 0.5, 0.1));
        return view(Optional.of(new BaselineComparison(drift,
                new RiskAssessment(OptionalDouble.of(0.7), OptionalDouble.of(0.5), OptionalDouble.of(0.2),
                        List.of(new DriftPenalty("layer", 2.0, 0.1, 0.2)), List.of()))));
    }

    private static GovernanceView withoutBaseline() {
        return view(Optional.empty());
    }

    private static GovernanceView view(Optional<BaselineComparison> comparison) {
        TraceableFinding excused = new TraceableFinding(
                "security", Optional.of(EXCEPTION.target()), Optional.empty(), Optional.of(EXCEPTION));
        TraceableFinding layerFinding = new TraceableFinding(
                "layer",
                Optional.of(new ExceptionTarget.OfEdge(
                        "com.example.OrderController", "com.example.OrderRepository", RelationType.CALL)),
                Optional.of("orders"),
                Optional.empty());

        return new GovernanceView(
                "defect-sample",
                Optional.of("0f1e2d"),
                List.of(new DimensionObservation("layer", OptionalDouble.of(0.5), OptionalDouble.of(1.0), 2, 1)),
                OptionalDouble.of(0.5),
                OptionalDouble.of(0.5),
                Optional.of(MaturityLevel.L1_REACTIVE),
                OptionalDouble.of(1.0),
                comparison,
                List.of(layerFinding, excused),
                List.of(new ViolatedConstraint("entropy_budget", "warning", 0, OptionalDouble.of(4.0), List.of())),
                InvariantAggregate.undeclared(),
                List.of());
    }
}
