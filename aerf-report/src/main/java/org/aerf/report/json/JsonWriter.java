package org.aerf.report.json;

import java.util.Iterator;
import java.util.Map;

/**
 * Serializes a {@link JsonValue} tree to a JSON string.
 *
 * <p>Numbers are rendered via {@link Double#toString(double)} — Java's
 * own deterministic algorithm for the shortest decimal that round-trips
 * exactly back to the same {@code double} — rather than rounding to a
 * fixed number of decimal places. Rounding for display would lose
 * precision that a later exact-reproducibility check might need; §14's
 * determinism principle is read here as "identical input serializes to
 * identical output," not "output looks tidy."
 */
public final class JsonWriter {

    private JsonWriter() {
    }

    public static String write(JsonValue value) {
        StringBuilder out = new StringBuilder();
        writeValue(value, out);
        return out.toString();
    }

    private static void writeValue(JsonValue value, StringBuilder out) {
        switch (value) {
            case JsonValue.JsonObject object -> writeObject(object, out);
            case JsonValue.JsonArray array -> writeArray(array, out);
            case JsonValue.JsonString string -> writeString(string.value(), out);
            case JsonValue.JsonNumber number -> out.append(Double.toString(number.value()));
            case JsonValue.JsonBoolean bool -> out.append(bool.value());
            case JsonValue.JsonNull ignored -> out.append("null");
        }
    }

    private static void writeObject(JsonValue.JsonObject object, StringBuilder out) {
        out.append('{');
        Iterator<Map.Entry<String, JsonValue>> entries = object.members().entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, JsonValue> entry = entries.next();
            writeString(entry.getKey(), out);
            out.append(':');
            writeValue(entry.getValue(), out);
            if (entries.hasNext()) {
                out.append(',');
            }
        }
        out.append('}');
    }

    private static void writeArray(JsonValue.JsonArray array, StringBuilder out) {
        out.append('[');
        Iterator<JsonValue> elements = array.elements().iterator();
        while (elements.hasNext()) {
            writeValue(elements.next(), out);
            if (elements.hasNext()) {
                out.append(',');
            }
        }
        out.append(']');
    }

    private static void writeString(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
