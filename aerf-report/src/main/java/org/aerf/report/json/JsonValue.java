package org.aerf.report.json;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A minimal JSON value algebra. Deliberately small and closed — exactly
 * the six JSON value kinds, nothing more — since this exists only to
 * serialize AERF's own already-typed domain objects, not to be a
 * general-purpose JSON library.
 */
public sealed interface JsonValue {

    /** Insertion-ordered, per AERF v0.4 section 14's determinism principle: identical input must serialize identically. */
    record JsonObject(Map<String, JsonValue> members) implements JsonValue {
        public JsonObject {
            // LinkedHashMap wrapped as unmodifiable, not Map.copyOf: Map.copyOf does not
            // preserve iteration order (the same pitfall found and fixed in aerf-model's
            // Graph in Increment 1), which would silently break deterministic serialization.
            members = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(members, "members")));
        }
    }

    record JsonArray(List<JsonValue> elements) implements JsonValue {
        public JsonArray {
            elements = List.copyOf(Objects.requireNonNull(elements, "elements"));
        }
    }

    record JsonString(String value) implements JsonValue {
        public JsonString {
            Objects.requireNonNull(value, "value");
        }
    }

    record JsonNumber(double value) implements JsonValue {
        public JsonNumber {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("JSON has no representation for non-finite numbers: " + value);
            }
        }
    }

    record JsonBoolean(boolean value) implements JsonValue {
    }

    enum JsonNull implements JsonValue {
        INSTANCE
    }
}
