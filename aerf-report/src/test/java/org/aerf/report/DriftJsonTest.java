package org.aerf.report;

import org.aerf.analysis.calibration.DimensionDrift;
import org.aerf.report.json.JsonWriter;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriftJsonTest {

    @Test
    void oneDimensionSerializesItsBaselineCurrentAndDelta() {
        // 0.25 and 0.5 are both exact in binary floating point, so the
        // expected delta (0.25) has no representation-rounding surprises
        // to account for in the assertion.
        Map<String, DimensionDrift> drift = Map.of("layer", new DimensionDrift("layer", 0.25, 0.5, 0.25));

        String json = JsonWriter.write(DriftJson.drift(drift));

        assertTrue(json.contains("\"baselineValue\":0.25"));
        assertTrue(json.contains("\"currentValue\":0.5"));
        assertTrue(json.contains("\"delta\":0.25"));
    }

    @Test
    void anEmptyDriftMapSerializesAsAnEmptyObject() {
        assertEquals("{}", JsonWriter.write(DriftJson.drift(Map.of())));
    }

    @Test
    void multipleDimensionsPreserveInsertionOrder() {
        // JsonObjectBuilder/JsonWriter already prove insertion order in
        // general (JsonWriterTest.writesObjectsInInsertionOrderNotHashOrder);
        // this confirms DriftJson doesn't introduce its own reordering,
        // e.g. by routing through something keyed on hash order.
        Map<String, DimensionDrift> drift = new LinkedHashMap<>();
        drift.put("security", new DimensionDrift("security", 0.0, 0.0, 0.0));
        drift.put("layer", new DimensionDrift("layer", 0.1, 0.2, 0.1));
        drift.put("cycle", new DimensionDrift("cycle", 0.5, 0.3, -0.2));

        String json = JsonWriter.write(DriftJson.drift(drift));

        int securityIndex = json.indexOf("security");
        int layerIndex = json.indexOf("layer");
        int cycleIndex = json.indexOf("cycle");
        assertTrue(securityIndex < layerIndex && layerIndex < cycleIndex,
                "expected insertion order security, layer, cycle but got: " + json);
    }
}
