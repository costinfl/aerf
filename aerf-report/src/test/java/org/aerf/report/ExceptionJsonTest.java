package org.aerf.report;

import org.aerf.analysis.governance.ApprovedException;
import org.aerf.analysis.governance.ExceptionLedger;
import org.aerf.analysis.governance.ExceptionTarget;
import org.aerf.analysis.governance.ExcusedFinding;
import org.aerf.model.RelationType;
import org.aerf.report.json.JsonWriter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Increment 28 (OQ-09): the governance verdict, serialized beside the measurements. */
class ExceptionJsonTest {

    @Test
    void anEmptyLedgerEmitsBothArraysRatherThanOmittingThem() {
        // "Nothing is excused" and "no exception went stale" are findings
        // in their own right, not absences.
        assertEquals("{\"excused\":[],\"unmatched\":[]}",
                JsonWriter.write(ExceptionJson.ledger(ExceptionLedger.empty())));
    }

    @Test
    void anExcusedFindingCarriesItsDimensionTargetReasonAndApprover() {
        ApprovedException exception = new ApprovedException(
                new ExceptionTarget.OfEdge("com.example.A", "com.example.B", RelationType.CALL),
                "batched at the JDBC layer", "alice");

        String json = JsonWriter.write(ExceptionJson.ledger(new ExceptionLedger(
                List.of(new ExcusedFinding("persistence", exception.target(), exception)), List.of())));

        assertEquals("{\"excused\":[{\"dimension\":\"persistence\",\"target\":{\"kind\":\"edge\","
                        + "\"sourceId\":\"com.example.A\",\"targetId\":\"com.example.B\",\"relation\":\"CALL\"},"
                        + "\"reason\":\"batched at the JDBC layer\",\"approvedBy\":\"alice\"}],"
                        + "\"unmatched\":[]}",
                json);
    }

    @Test
    void aStaleExceptionIsSerializedSoItCannotVanishQuietly() {
        ApprovedException stale = new ApprovedException(
                new ExceptionTarget.OfNode("com.example.LongDeleted"), "was a known issue", "bob");

        String json = JsonWriter.write(ExceptionJson.ledger(new ExceptionLedger(List.of(), List.of(stale))));

        assertTrue(json.contains("\"unmatched\":[{\"target\":{\"kind\":\"node\","
                + "\"nodeId\":\"com.example.LongDeleted\"},\"reason\":\"was a known issue\","
                + "\"approvedBy\":\"bob\"}]"), json);
    }

    @Test
    void aDeclarationSerializesTheSameWayWhereverItAppears() {
        // The same writer serves the governance declaration block and the
        // unmatched list, so a reader compares like with like.
        ApprovedException exception = new ApprovedException(
                new ExceptionTarget.OfNode("com.example.A"), "accepted", "alice");

        assertTrue(JsonWriter.write(ExceptionJson.ledger(new ExceptionLedger(List.of(), List.of(exception))))
                .contains(JsonWriter.write(ExceptionJson.approvedException(exception))));
    }
}
