package org.aerf.analysis.calibration;

import org.aerf.analysis.governance.DriftSensitivity;
import org.aerf.analysis.governance.WeightedDriftDimension;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Increment 30 (OQ-14): §5.3's
 * {@code R = sum(w_d * f_d(E_d)) + beta * sum(gamma_d * max(0, Delta_d))}.
 * See {@code docs/increment-30-drift-aware-risk-model.md}.
 */
class RiskTest {

    private static final String FINGERPRINT = "policy-a";

    @Test
    void theEntropyTermAndDriftTermStaySeparatelyVisibleBesideTheTotal() {
        // OQ-14's binding constraint made concrete: a reader must be able
        // to tell "this architecture is in poor shape" from "this
        // architecture got worse recently".
        RiskAssessment risk = compute(OptionalDouble.of(0.4),
                drift("layer", 0.2, 0.5), sensitivity(2.0, weight("layer", 1.0)));

        assertEquals(OptionalDouble.of(0.4), risk.entropyTerm());
        assertEquals(OptionalDouble.of(2.0 * 0.3), risk.driftTerm(),
                "beta 2.0 times a 0.3 worsening weighted 1.0");
        assertEquals(OptionalDouble.of(0.4 + 0.6), risk.value());
    }

    @Test
    void onlyWorseningContributesToThePenalty() {
        // delta = current - baseline and higher entropy is worse, so
        // section 5.3's max(0, Delta_d) drops improvement.
        RiskAssessment risk = compute(OptionalDouble.of(0.4),
                drift("layer", 0.5, 0.1), sensitivity(2.0, weight("layer", 1.0)));

        assertEquals(OptionalDouble.of(0.0), risk.driftTerm());
        assertEquals(OptionalDouble.of(0.4), risk.value(), "risk is the entropy term alone");
        assertEquals(-0.4, risk.penalties().get(0).delta(),
                "the raw delta is still reported, so the improvement is visible");
        assertEquals(0.0, risk.penalties().get(0).penalty());
    }

    @Test
    void anImprovementInOneDimensionDoesNotOffsetARegressionInAnother() {
        Map<String, DimensionDrift> drift = new LinkedHashMap<>();
        drift.put("layer", new DimensionDrift("layer", 0.1, 0.4, 0.3));
        drift.put("cycle", new DimensionDrift("cycle", 0.9, 0.1, -0.8));

        RiskAssessment risk = compute(OptionalDouble.of(0.0), drift,
                sensitivity(1.0, weight("layer", 1.0), weight("cycle", 1.0)));

        assertEquals(OptionalDouble.of(0.3), risk.driftTerm(),
                "the 0.8 improvement contributes 0, it does not cancel the 0.3 regression");
    }

    @Test
    void betaScalesTheWholeDriftTermAndNotTheEntropyTerm() {
        Map<String, DimensionDrift> drift = drift("layer", 0.1, 0.3);

        assertEquals(OptionalDouble.of(0.5 + 0.2),
                compute(OptionalDouble.of(0.5), drift, sensitivity(1.0, weight("layer", 1.0))).value());
        assertEquals(OptionalDouble.of(0.5 + 0.6),
                compute(OptionalDouble.of(0.5), drift, sensitivity(3.0, weight("layer", 1.0))).value());
    }

    @Test
    void everyPenaltyStaysVisibleBesideTheDriftTerm() {
        Map<String, DimensionDrift> drift = new LinkedHashMap<>();
        drift.put("layer", new DimensionDrift("layer", 0.1, 0.3, 0.2));
        drift.put("cycle", new DimensionDrift("cycle", 0.0, 0.5, 0.5));

        RiskAssessment risk = compute(OptionalDouble.of(0.0), drift,
                sensitivity(1.0, weight("layer", 2.0), weight("cycle", 1.0)));

        assertEquals(List.of("layer", "cycle"),
                risk.penalties().stream().map(DriftPenalty::dimension).toList());
        assertEquals(risk.driftTerm().getAsDouble(),
                risk.penalties().stream().mapToDouble(DriftPenalty::penalty).sum(),
                "beta is 1.0 here, so the drift term is exactly the sum of the penalties beside it");
    }

    @Test
    void aNonzeroGammaDimensionMissingFromTheDriftMapMakesRiskUndefinedAndIsNamed() {
        // Drift omits a dimension undefined at either endpoint. Treating
        // that as a zero penalty would claim "no worsening observed"
        // where nothing was comparable.
        RiskAssessment risk = compute(OptionalDouble.of(0.4),
                drift("layer", 0.1, 0.3), sensitivity(1.0, weight("layer", 1.0), weight("security", 2.0)));

        assertTrue(risk.value().isEmpty());
        assertTrue(risk.undefinedBecause().stream().anyMatch(r -> r.contains("security")),
                risk.undefinedBecause().toString());
    }

    @Test
    void aZeroWeightedDimensionBeingUnmeasurableDoesNotBlockRisk() {
        RiskAssessment risk = compute(OptionalDouble.of(0.4),
                drift("layer", 0.1, 0.3), sensitivity(1.0, weight("layer", 1.0), weight("security", 0.0)));

        assertEquals(OptionalDouble.of(0.6), risk.value(),
                "security contributes nothing at gamma 0, so its being unmeasurable is irrelevant");
    }

    @Test
    void anUndefinedTotalEntropyMakesRiskUndefined() {
        RiskAssessment risk = compute(OptionalDouble.empty(),
                drift("layer", 0.1, 0.3), sensitivity(1.0, weight("layer", 1.0)));

        assertTrue(risk.value().isEmpty());
        assertTrue(risk.undefinedBecause().stream().anyMatch(r -> r.contains("total entropy")),
                risk.undefinedBecause().toString());
    }

    @Test
    void differingGovernanceFingerprintsMakeRiskUndefinedWithAStatedReason() {
        // The finding-B fix: a risk number computed across a silent policy
        // change is worse than no number, because the "drift" it reports
        // may be nothing but a changed layering matrix.
        RiskAssessment risk = Risk.compute(OptionalDouble.of(0.4),
                snapshot("policy-a"), snapshot("policy-b"),
                drift("layer", 0.1, 0.3), sensitivity(1.0, weight("layer", 1.0)));

        assertTrue(risk.value().isEmpty());
        assertTrue(risk.undefinedBecause().stream().anyMatch(r -> r.contains("different policies")),
                risk.undefinedBecause().toString());
    }

    @Test
    void anUnrecordedGovernanceFingerprintIsNotTreatedAsAMatch() {
        // Unknown is not the same as matching - a baseline stored before
        // increment 30 genuinely has no recorded policy.
        RiskAssessment risk = Risk.compute(OptionalDouble.of(0.4),
                EntropySnapshot.withoutGovernanceIdentity("s", Map.of()), snapshot(FINGERPRINT),
                drift("layer", 0.1, 0.3), sensitivity(1.0, weight("layer", 1.0)));

        assertTrue(risk.value().isEmpty());
        assertTrue(risk.undefinedBecause().stream().anyMatch(r -> r.contains("unrecorded")),
                risk.undefinedBecause().toString());
    }

    @Test
    void noDeclaredSensitivityLeavesRiskUndefinedRatherThanReturningEntropyAlone() {
        // Reporting E_total under the name R would quietly present a
        // different quantity than the one that was asked for.
        RiskAssessment risk = compute(OptionalDouble.of(0.4),
                drift("layer", 0.1, 0.3), DriftSensitivity.none());

        assertTrue(risk.value().isEmpty());
        assertEquals(OptionalDouble.of(0.4), risk.entropyTerm(),
                "the term that was computable stays visible");
    }

    @Test
    void anUndefinedRiskStillReportsThePenaltiesItCouldCompute() {
        RiskAssessment risk = compute(OptionalDouble.empty(),
                drift("layer", 0.1, 0.3), sensitivity(1.0, weight("layer", 1.0)));

        assertEquals(1, risk.penalties().size(),
                "hiding a computable term would lose the evidence a reader needs");
    }

    @Test
    void violationsAreNotATermInR() {
        assertTrue(Arrays.stream(Risk.class.getDeclaredMethods())
                        .flatMap(m -> Arrays.stream(m.getParameterTypes()))
                        .noneMatch(t -> t.getName().contains("Invariant") || t.getName().contains("Ledger")),
                "section 5.3 states two terms and says nothing about violations");
    }

    @Test
    void aDefinedRiskCannotAlsoCarryReasonsItIsUndefined() {
        assertThrows(IllegalArgumentException.class, () -> new RiskAssessment(
                OptionalDouble.of(1.0), OptionalDouble.of(1.0), OptionalDouble.of(0.0),
                List.of(), List.of("contradictory")));
    }

    @Test
    void resultIsDeterministicAcrossRepeatedRuns() {
        Map<String, DimensionDrift> drift = drift("layer", 0.1, 0.4);
        DriftSensitivity sensitivity = sensitivity(2.0, weight("layer", 1.5));

        assertEquals(compute(OptionalDouble.of(0.3), drift, sensitivity),
                compute(OptionalDouble.of(0.3), drift, sensitivity));
    }

    private static RiskAssessment compute(OptionalDouble totalEntropy, Map<String, DimensionDrift> drift,
                                          DriftSensitivity sensitivity) {
        return Risk.compute(totalEntropy, snapshot(FINGERPRINT), snapshot(FINGERPRINT), drift, sensitivity);
    }

    private static EntropySnapshot snapshot(String fingerprint) {
        return new EntropySnapshot("subject", Map.of(), Optional.of(fingerprint));
    }

    private static Map<String, DimensionDrift> drift(String dimension, double baseline, double current) {
        return Map.of(dimension, new DimensionDrift(dimension, baseline, current, current - baseline));
    }

    private static DriftSensitivity sensitivity(double beta, WeightedDriftDimension... weights) {
        return DriftSensitivity.of(beta, List.of(weights));
    }

    private static WeightedDriftDimension weight(String dimension, double weight) {
        return new WeightedDriftDimension(dimension, weight);
    }
}
