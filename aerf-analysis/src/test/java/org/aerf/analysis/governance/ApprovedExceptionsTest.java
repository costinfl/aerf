package org.aerf.analysis.governance;

import org.aerf.model.RelationType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Increment 28 (OQ-09): at most one exception may claim a finding. */
class ApprovedExceptionsTest {

    @Test
    void twoExceptionsTargetingTheSameFindingAreRejected() {
        // An excused finding has an owner, and two owners for one finding
        // is an ambiguity nothing downstream could resolve - the same
        // reason Subsystems refuses overlapping prefixes.
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> ApprovedExceptions.of(List.of(
                        new ApprovedException(node("a"), "legacy", "alice"),
                        new ApprovedException(node("a"), "also legacy", "bob"))));

        assertTrue(thrown.getMessage().contains("two owners"), thrown.getMessage());
    }

    @Test
    void twoExceptionsForDifferentRelationsBetweenTheSameNodesAreBothAllowed() {
        ApprovedExceptions exceptions = ApprovedExceptions.of(List.of(
                new ApprovedException(edge("a", "b", RelationType.CALL), "one", "alice"),
                new ApprovedException(edge("a", "b", RelationType.DEPENDS), "two", "bob")));

        assertEquals("alice", exceptions.excusing(edge("a", "b", RelationType.CALL)).orElseThrow().approvedBy());
        assertEquals("bob", exceptions.excusing(edge("a", "b", RelationType.DEPENDS)).orElseThrow().approvedBy());
    }

    @Test
    void declaringNothingIsTheOrdinaryCase() {
        assertTrue(ApprovedExceptions.none().isEmpty());
        assertTrue(ApprovedExceptions.of(List.of()).isEmpty());
        assertTrue(ApprovedExceptions.none().excusing(node("anything")).isEmpty());
    }

    @Test
    void exceptionsReadBackInTheOrderTheyWereDeclared() {
        ApprovedExceptions exceptions = ApprovedExceptions.of(List.of(
                new ApprovedException(node("b"), "second-declared", "alice"),
                new ApprovedException(node("a"), "first-declared", "bob")));

        assertEquals(List.of("second-declared", "first-declared"),
                exceptions.declared().stream().map(ApprovedException::reason).toList());
    }

    @Test
    void theDeclaredListCannotBeMutatedThroughTheAccessor() {
        ApprovedExceptions exceptions = ApprovedExceptions.of(List.of(
                new ApprovedException(node("a"), "legacy", "alice")));

        assertThrows(UnsupportedOperationException.class,
                () -> exceptions.declared().add(new ApprovedException(node("b"), "sneaked", "nobody")));
    }

    @Test
    void lookupIsIndependentOfDeclarationOrder() {
        ApprovedException forA = new ApprovedException(node("a"), "legacy", "alice");
        ApprovedException forB = new ApprovedException(node("b"), "legacy", "bob");

        assertEquals(exceptionsOf(forA, forB).excusing(node("b")),
                exceptionsOf(forB, forA).excusing(node("b")));
    }

    private static ApprovedExceptions exceptionsOf(ApprovedException... declared) {
        return ApprovedExceptions.of(List.of(declared));
    }

    private static ExceptionTarget node(String id) {
        return new ExceptionTarget.OfNode(id);
    }

    private static ExceptionTarget edge(String source, String target, RelationType relation) {
        return new ExceptionTarget.OfEdge(source, target, relation);
    }
}
