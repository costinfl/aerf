package org.aerf.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
