package org.aerf.pipeline;

import org.aerf.model.Node;
import org.aerf.model.Role;
import org.aerf.report.json.JsonWriter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Finding R's reachability proof: the evidence that decides a role is now
 * visible in the report that states the role.
 *
 * <p>This is the test that could not be written before the fix, and it is
 * the reason the fix matters. {@code DefaultSeedRules} assigns
 * {@code PRESENTATION} by matching
 * {@code evidence.attributes().get("annotation")} against the resolved
 * fully-qualified name {@code org.springframework.stereotype.Controller}.
 * That attribute existed in memory and drove the outcome, but
 * {@code GraphJson} dropped it — so a reader of the report saw a role, saw
 * a free-text description mentioning an annotation, and had no way to
 * check that the rule had matched a real {@code @Controller} rather than
 * some unrelated framework's same-named one.
 *
 * <p>§3.5 requires evidence to stay traceable. A conclusion whose deciding
 * input is unserialized is not traceable, whatever the description says.
 */
class EvidenceTraceabilityEndToEndTest {

    private static final String ANNOTATION_ATTRIBUTE = "annotation";
    private static final String SPRING_CONTROLLER = "org.springframework.stereotype.Controller";

    @Test
    void theAttributeThatDecidedARoleIsVisibleInTheReportThatStatesTheRole() {
        PipelineReport report = Pipeline.run(PipelineTest.config());

        Node controller = report.graph().nodes().stream()
                .filter(node -> node.role() == Role.PRESENTATION)
                .findFirst()
                .orElseThrow(() -> new AssertionError("this fixture has a @Controller"));

        // In memory: the attribute is what the seed rule matched on.
        assertTrue(controller.evidence().stream()
                        .anyMatch(e -> SPRING_CONTROLLER.equals(e.attributes().get(ANNOTATION_ATTRIBUTE))),
                "precondition - the role came from a resolved annotation attribute: " + controller.evidence());

        // In the report: it is now there too.
        String json = JsonWriter.write(Main.toJson(report));
        assertTrue(json.contains("\"" + ANNOTATION_ATTRIBUTE + "\":\"" + SPRING_CONTROLLER + "\""),
                "the deciding attribute must appear in the report, not only in memory");
    }

    @Test
    void aReaderCanDistinguishARealAnnotationFromOneThatMerelySharesItsSimpleName() {
        // The free-text description is identical whether the annotation
        // resolved to Spring's type or to an unrelated one, because it is
        // built from the simple name. Only the attribute carries the FQN,
        // which is why dropping it lost the distinction the extractor took
        // care to make.
        String json = JsonWriter.write(Main.toJson(Pipeline.run(PipelineTest.config())));

        assertTrue(json.contains("\"description\":\"@Controller annotation observed\""), json);
        assertTrue(json.contains(SPRING_CONTROLLER), "only the attribute says which @Controller");
    }

    @Test
    void anEvidenceItemWithoutAttributesStillReportsThemAsAnEmptyObject() {
        // Absent and empty are different claims. Every node evidence item
        // and every edge provenance item carries the key, so a consumer
        // never has to guess whether the adapter recorded nothing or the
        // serializer forgot.
        String json = JsonWriter.write(Main.toJson(Pipeline.run(PipelineTest.config())));

        assertTrue(json.contains("\"attributes\":{}"),
                "plain declaration evidence has no attributes and says so");
        assertFalse(json.contains("\"executionContext\":\"UNKNOWN\"}"),
                "attributes is the last key of an evidence object, so no evidence object ends at "
                        + "executionContext any more");
    }

    @Test
    void everyEvidenceItemInTheReportCarriesTheKey() {
        // Counted structurally rather than from the graph's own totals,
        // because an edge's provenance is serialized more than once: the
        // same edge appears under `graph`, and again under
        // `layerEntropy.relevantEdges`, `violatingEdges`,
        // `persistenceEntropy.relevantEdges` and `flaggedEdges`. So the
        // invariant that actually holds is per evidence OBJECT wherever it
        // is rendered: exactly one `executionContext` and exactly one
        // `attributes`, with nodes contributing the one extra `attributes`
        // they have always had.
        PipelineReport report = Pipeline.run(PipelineTest.config());
        String json = JsonWriter.write(Main.toJson(report));

        long evidenceObjects = countOccurrences(json, "\"executionContext\":");
        long attributeKeys = countOccurrences(json, "\"attributes\":");
        long nodeObjects = countOccurrences(json, "\"role\":");

        assertEquals(evidenceObjects + nodeObjects, attributeKeys,
                "every evidence object carries attributes, and so does every node - no renderer was missed");
    }

    @Test
    void identicalRunsStillSerializeIdentically() {
        // Evidence keeps its attributes in a LinkedHashMap so that section
        // 14's "identical sources produce identical output" survives the
        // new key.
        assertEquals(JsonWriter.write(Main.toJson(Pipeline.run(PipelineTest.config()))),
                JsonWriter.write(Main.toJson(Pipeline.run(PipelineTest.config()))));
    }

    private static long countOccurrences(String haystack, String needle) {
        long count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }
}
