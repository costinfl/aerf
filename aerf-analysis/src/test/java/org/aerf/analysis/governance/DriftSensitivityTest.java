package org.aerf.analysis.governance;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Increment 30 (OQ-14): where §5.3's beta and gamma_d live. */
class DriftSensitivityTest {

    @Test
    void betaAndGammaMustBothBeNonNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> DriftSensitivity.of(-0.1, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new WeightedDriftDimension("layer", -1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new WeightedDriftDimension(" ", 1.0));
    }

    @Test
    void gammaWeightsNeedNotSumToOne() {
        // Section 5.1 constrains w_d with sum = 1; section 5.3 states no
        // such constraint on gamma_d, exactly as section 6.1 states none
        // on lambda_k. Inventing one would assert what v0.4 declines to.
        DriftSensitivity sensitivity = DriftSensitivity.of(1.0, List.of(
                new WeightedDriftDimension("layer", 4.0),
                new WeightedDriftDimension("cycle", 7.0)));

        assertEquals(OptionalDouble.of(4.0), sensitivity.weightFor("layer"));
        assertEquals(OptionalDouble.of(7.0), sensitivity.weightFor("cycle"));
    }

    @Test
    void aDimensionMayCarryOnlyOneDriftWeight() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> DriftSensitivity.of(1.0, List.of(
                        new WeightedDriftDimension("layer", 1.0),
                        new WeightedDriftDimension("layer", 2.0))));

        assertTrue(thrown.getMessage().contains("layer"), thrown.getMessage());
    }

    @Test
    void declaringNothingLeavesBetaUndefinedRatherThanZero() {
        // Zero beta is a declared position ("drift does not matter to
        // us"); no declaration is silence, and R reads undefined.
        assertTrue(DriftSensitivity.none().isEmpty());
        assertTrue(DriftSensitivity.none().beta().isEmpty());
        assertEquals(OptionalDouble.of(0.0), DriftSensitivity.of(0.0, List.of()).beta());
        assertFalse(DriftSensitivity.of(0.0, List.of()).isEmpty(),
                "beta 0.0 is declared, so this is not the undeclared case");
    }

    @Test
    void weightsReadBackInTheOrderTheyWereDeclared() {
        DriftSensitivity sensitivity = DriftSensitivity.of(1.0, List.of(
                new WeightedDriftDimension("cycle", 1.0),
                new WeightedDriftDimension("layer", 2.0)));

        assertEquals(List.of("cycle", "layer"),
                sensitivity.declared().stream().map(WeightedDriftDimension::dimension).toList());
    }

    @Test
    void anUndeclaredDimensionHasNoWeight() {
        assertTrue(DriftSensitivity.of(1.0, List.of(new WeightedDriftDimension("layer", 1.0)))
                .weightFor("security").isEmpty());
    }

    @Test
    void theDeclaredListCannotBeMutatedThroughTheAccessor() {
        DriftSensitivity sensitivity = DriftSensitivity.of(1.0, List.of());

        assertThrows(UnsupportedOperationException.class,
                () -> sensitivity.declared().add(new WeightedDriftDimension("sneaked", 1.0)));
    }
}
