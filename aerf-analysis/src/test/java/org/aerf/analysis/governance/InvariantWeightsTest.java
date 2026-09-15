package org.aerf.analysis.governance;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Increment 29 (OQ-15): where §6.1's lambda_k lives. */
class InvariantWeightsTest {

    @Test
    void anInvariantNameAndANonNegativeWeightAreRequired() {
        assertThrows(NullPointerException.class, () -> new WeightedInvariant(null, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new WeightedInvariant(" ", 1.0));
        assertThrows(IllegalArgumentException.class, () -> new WeightedInvariant("budget", -0.1));
    }

    @Test
    void weightsNeedNotSumToOneUnlikeAnEntropyCalibrationProfile() {
        // The one structural difference from CalibrationProfile, and it is
        // the specification's: section 5.1 constrains entropy weights with
        // sum(w_d) = 1, section 6.1 states no such constraint on lambda_k.
        // Inventing one would assert something v0.4 declines to.
        InvariantWeights weights = InvariantWeights.of(List.of(
                new WeightedInvariant("a", 3.0),
                new WeightedInvariant("b", 5.0)));

        assertEquals(2, weights.declared().size());
    }

    @Test
    void aWeightHasNoUpperBound() {
        assertEquals(OptionalDouble.of(1000.0),
                InvariantWeights.of(List.of(new WeightedInvariant("critical", 1000.0))).weightFor("critical"));
    }

    @Test
    void anInvariantMayCarryOnlyOneWeight() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> InvariantWeights.of(List.of(
                        new WeightedInvariant("budget", 1.0),
                        new WeightedInvariant("budget", 2.0))));

        assertTrue(thrown.getMessage().contains("budget"), thrown.getMessage());
    }

    @Test
    void declaringNoWeightsIsRepresentableUnlikeAnEmptyCalibrationProfile() {
        // An empty CalibrationProfile cannot exist - it would fail the
        // sum-to-one check. An empty InvariantWeights can, and means
        // E_inv is undefined for this run.
        assertTrue(InvariantWeights.none().isEmpty());
        assertTrue(InvariantWeights.of(List.of()).isEmpty());
        assertTrue(InvariantWeights.none().weightFor("anything").isEmpty());
    }

    @Test
    void weightsReadBackInTheOrderTheyWereDeclared() {
        InvariantWeights weights = InvariantWeights.of(List.of(
                new WeightedInvariant("second", 2.0),
                new WeightedInvariant("first", 1.0)));

        assertEquals(List.of("second", "first"),
                weights.declared().stream().map(WeightedInvariant::invariantName).toList());
    }

    @Test
    void lookupIsIndependentOfDeclarationOrder() {
        WeightedInvariant a = new WeightedInvariant("a", 1.0);
        WeightedInvariant b = new WeightedInvariant("b", 2.0);

        assertEquals(InvariantWeights.of(List.of(a, b)).weightFor("b"),
                InvariantWeights.of(List.of(b, a)).weightFor("b"));
    }

    @Test
    void theDeclaredListCannotBeMutatedThroughTheAccessor() {
        InvariantWeights weights = InvariantWeights.of(List.of(new WeightedInvariant("a", 1.0)));

        assertThrows(UnsupportedOperationException.class,
                () -> weights.declared().add(new WeightedInvariant("sneaked", 1.0)));
    }

    @Test
    void aZeroWeightIsAllowedAndIsADeclaredPositionNotAnAbsence() {
        assertEquals(OptionalDouble.of(0.0),
                InvariantWeights.of(List.of(new WeightedInvariant("informational", 0.0)))
                        .weightFor("informational"));
    }
}
