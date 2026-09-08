package org.aerf.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceTest {

    @Test
    void locationIsOptionalWhenNotProvided() {
        Evidence evidence = Evidence.of("java", "class declaration observed", ExtractionFidelity.L1_SYNTAX);

        assertTrue(evidence.location().isEmpty());
    }

    @Test
    void equalFieldsProduceEqualEvidence() {
        Evidence a = Evidence.of("spring", "@Controller annotation", "OrderController.java:12", ExtractionFidelity.L2_SYMBOL_RESOLVED);
        Evidence b = Evidence.of("spring", "@Controller annotation", "OrderController.java:12", ExtractionFidelity.L2_SYMBOL_RESOLVED);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void blankDescriptionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Evidence.of("java", "  ", ExtractionFidelity.L1_SYNTAX));
    }

    @Test
    void executionContextDefaultsToUnknownNotSingle() {
        Evidence evidence = Evidence.of("java", "repository call observed", ExtractionFidelity.L2_SYMBOL_RESOLVED);

        assertEquals(ExecutionContext.UNKNOWN, evidence.executionContext());
    }

    @Test
    void executionContextCanBeSetExplicitly() {
        Evidence iterated = Evidence.of("java", "call inside for loop", ExtractionFidelity.L2_SYMBOL_RESOLVED, ExecutionContext.ITERATED);
        Evidence single = Evidence.of("java", "call outside any loop", "Foo.java:9", ExtractionFidelity.L2_SYMBOL_RESOLVED, ExecutionContext.SINGLE);

        assertEquals(ExecutionContext.ITERATED, iterated.executionContext());
        assertEquals(ExecutionContext.SINGLE, single.executionContext());
    }

    @Test
    void evidenceDifferingOnlyByExecutionContextIsNotEqual() {
        Evidence unknown = Evidence.of("java", "repository call observed", ExtractionFidelity.L2_SYMBOL_RESOLVED);
        Evidence iterated = Evidence.of("java", "repository call observed", ExtractionFidelity.L2_SYMBOL_RESOLVED, ExecutionContext.ITERATED);

        assertNotEquals(unknown, iterated);
    }
}
