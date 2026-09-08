package org.aerf.report.json;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonWriterTest {

    @Test
    void writesPrimitives() {
        assertEquals("\"hello\"", JsonWriter.write(new JsonValue.JsonString("hello")));
        assertEquals("true", JsonWriter.write(new JsonValue.JsonBoolean(true)));
        assertEquals("false", JsonWriter.write(new JsonValue.JsonBoolean(false)));
        assertEquals("null", JsonWriter.write(JsonValue.JsonNull.INSTANCE));
        assertEquals("1.0", JsonWriter.write(new JsonValue.JsonNumber(1.0)));
        assertEquals("0.5", JsonWriter.write(new JsonValue.JsonNumber(0.5)));
    }

    @Test
    void escapesControlAndSpecialCharactersInStrings() {
        String written = JsonWriter.write(new JsonValue.JsonString("line1\nline2\ttab\"quote\\backslash"));

        assertEquals("\"line1\\nline2\\ttab\\\"quote\\\\backslash\"", written);
    }

    @Test
    void escapesLowControlCharactersAsUnicodeEscapes() {
        String written = JsonWriter.write(new JsonValue.JsonString("\u0001"));

        assertEquals("\"\\u0001\"", written);
    }

    @Test
    void writesObjectsInInsertionOrderNotHashOrder() {
        JsonValue object = new JsonObjectBuilder()
                .put("zeta", 1.0)
                .put("alpha", 2.0)
                .put("mid", 3.0)
                .build();

        assertEquals("{\"zeta\":1.0,\"alpha\":2.0,\"mid\":3.0}", JsonWriter.write(object));
    }

    @Test
    void writesArraysAndNestedObjects() {
        JsonValue value = new JsonValue.JsonArray(List.of(
                new JsonValue.JsonNumber(1.0),
                new JsonObjectBuilder().put("k", "v").build()));

        assertEquals("[1.0,{\"k\":\"v\"}]", JsonWriter.write(value));
    }

    @Test
    void nonFiniteNumbersAreRejectedRatherThanProducingInvalidJson() {
        assertThrows(IllegalArgumentException.class, () -> new JsonValue.JsonNumber(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new JsonValue.JsonNumber(Double.POSITIVE_INFINITY));
    }

    @Test
    void writingTheSameValueTwiceProducesIdenticalOutput() {
        JsonValue value = new JsonObjectBuilder()
                .put("a", 1.0)
                .put("b", new JsonValue.JsonArray(List.of(new JsonValue.JsonString("x"), new JsonValue.JsonString("y"))))
                .build();

        assertEquals(JsonWriter.write(value), JsonWriter.write(value));
    }

    @Test
    void objectRejectsNullMapArgumentDefensively() {
        assertThrows(NullPointerException.class, () -> new JsonValue.JsonObject(null));
    }

    @Test
    void mapEntryOrderSurvivesEvenWithAdversarialKeyHashes() {
        // Keys chosen so String.hashCode order would not match insertion order.
        Map<String, JsonValue> ordered = new java.util.LinkedHashMap<>();
        ordered.put("zzz", new JsonValue.JsonNumber(1.0));
        ordered.put("a", new JsonValue.JsonNumber(2.0));
        ordered.put("mmm", new JsonValue.JsonNumber(3.0));

        JsonValue object = new JsonValue.JsonObject(ordered);

        assertEquals("{\"zzz\":1.0,\"a\":2.0,\"mmm\":3.0}", JsonWriter.write(object));
    }
}
