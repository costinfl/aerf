package org.aerf.analysis.governance;

import org.aerf.model.RelationType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Increment 28 (OQ-09): what constitutes an approved exception. */
class ApprovedExceptionTest {

    @Test
    void anExceptionWithoutAStatedReasonIsRejected() {
        // "Approved" with no justification is an unexplained hole, not an
        // approved exception - and the compiler is where that should be
        // caught, the same stance GovernancePolicy takes on every
        // governance choice.
        assertThrows(IllegalArgumentException.class,
                () -> new ApprovedException(node("a"), "  ", "alice"));
        assertThrows(NullPointerException.class,
                () -> new ApprovedException(node("a"), null, "alice"));
    }

    @Test
    void anExceptionWithoutANamedApproverIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ApprovedException(node("a"), "batched at the JDBC layer", ""));
        assertThrows(NullPointerException.class,
                () -> new ApprovedException(node("a"), "batched at the JDBC layer", null));
    }

    @Test
    void anExceptionNeedsATarget() {
        assertThrows(NullPointerException.class,
                () -> new ApprovedException(null, "reason", "alice"));
    }

    @Test
    void anEdgeTargetNamesSourceTargetAndRelation() {
        // AERF's graph is deliberately a multigraph, so naming the
        // relation is what keeps an exception from silently covering a
        // second finding its approver never looked at.
        ExceptionTarget.OfEdge target =
                new ExceptionTarget.OfEdge("com.example.A", "com.example.B", RelationType.CALL);

        assertEquals("com.example.A", target.sourceId());
        assertEquals(RelationType.CALL, target.relation());
        assertThrows(NullPointerException.class,
                () -> new ExceptionTarget.OfEdge("com.example.A", "com.example.B", null));
        assertThrows(IllegalArgumentException.class,
                () -> new ExceptionTarget.OfEdge(" ", "com.example.B", RelationType.CALL));
    }

    @Test
    void aTargetIsAnExactIdNotAPrefix() {
        // Deliberately unlike Subsystem, which selects a broad region by
        // prefix. An exception is a narrow admission about one reviewed
        // finding; a prefix would waive findings nobody has seen, including
        // ones that do not exist yet.
        ApprovedExceptions exceptions = ApprovedExceptions.of(List.of(
                new ApprovedException(node("com.example.Order"), "legacy", "alice")));

        assertTrue(exceptions.excusing(node("com.example.Order")).isPresent());
        assertTrue(exceptions.excusing(node("com.example.OrderLine")).isEmpty(),
                "a longer id that merely starts with the declared one is a different finding");
    }

    @Test
    void theTargetTypeHasNoGraphScopeVariant() {
        // A graph-scope violation names nothing addressable, and a cycle
        // finding is a set of nodes whose excusal needs its own semantics.
        assertEquals(2, ExceptionTarget.class.getPermittedSubclasses().length,
                "OfNode and OfEdge only - see the increment doc on why cycles and graph scope are deferred");
    }

    private static ExceptionTarget node(String id) {
        return new ExceptionTarget.OfNode(id);
    }
}
