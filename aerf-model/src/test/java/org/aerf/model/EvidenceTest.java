package org.aerf.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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

    @Test
    void attributesDefaultToEmptyForTheOfFactories() {
        Evidence evidence = Evidence.of("java", "class declaration observed", ExtractionFidelity.L1_SYNTAX);

        assertTrue(evidence.attributes().isEmpty());
    }

    @Test
    void builderAttachesStructuredAttributes() {
        Evidence evidence = Evidence.builder("spring", "@Controller annotation observed", ExtractionFidelity.L2_SYMBOL_RESOLVED)
                .attribute("annotation", "org.springframework.stereotype.Controller")
                .location("OrderController.java:12")
                .executionContext(ExecutionContext.SINGLE)
                .build();

        assertEquals(Map.of("annotation", "org.springframework.stereotype.Controller"), evidence.attributes());
        assertEquals("OrderController.java:12", evidence.location().orElseThrow());
        assertEquals(ExecutionContext.SINGLE, evidence.executionContext());
    }

    @Test
    void builderAttributesPreserveInsertionOrder() {
        Evidence evidence = Evidence.builder("spring", "binding observed", ExtractionFidelity.L2_SYMBOL_RESOLVED)
                .attribute("zeta", "1")
                .attribute("alpha", "2")
                .attribute("mid", "3")
                .build();

        assertEquals(List.copyOf(evidence.attributes().keySet()), List.of("zeta", "alpha", "mid"));
    }

    @Test
    void builderRejectsBlankAttributeKeys() {
        Evidence.Builder builder = Evidence.builder("java", "observed", ExtractionFidelity.L1_SYNTAX);

        assertThrows(IllegalArgumentException.class, () -> builder.attribute("  ", "value"));
    }

    @Test
    void evidenceDifferingOnlyByAttributesIsNotEqual() {
        Evidence withoutAttribute = Evidence.of("spring", "@Controller annotation", ExtractionFidelity.L2_SYMBOL_RESOLVED);
        Evidence withAttribute = Evidence.builder("spring", "@Controller annotation", ExtractionFidelity.L2_SYMBOL_RESOLVED)
                .attribute("annotation", "org.springframework.stereotype.Controller")
                .build();

        assertNotEquals(withoutAttribute, withAttribute);
    }

    @Test
    void attributesMapIsImmutable() {
        Evidence evidence = Evidence.builder("spring", "observed", ExtractionFidelity.L1_SYNTAX)
                .attribute("k", "v")
                .build();

        assertThrows(UnsupportedOperationException.class, () -> evidence.attributes().put("x", "y"));
    }
}
