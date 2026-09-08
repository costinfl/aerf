package org.aerf.report.json;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Ergonomic, order-preserving construction of a {@link JsonValue.JsonObject}. */
public final class JsonObjectBuilder {

    private final Map<String, JsonValue> members = new LinkedHashMap<>();

    public JsonObjectBuilder put(String key, JsonValue value) {
        members.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
        return this;
    }

    public JsonObjectBuilder put(String key, String value) {
        return put(key, new JsonValue.JsonString(value));
    }

    public JsonObjectBuilder put(String key, double value) {
        return put(key, new JsonValue.JsonNumber(value));
    }

    public JsonObjectBuilder put(String key, boolean value) {
        return put(key, new JsonValue.JsonBoolean(value));
    }

    public JsonObjectBuilder putNullableString(String key, String value) {
        return put(key, value == null ? JsonValue.JsonNull.INSTANCE : new JsonValue.JsonString(value));
    }

    public JsonValue.JsonObject build() {
        return new JsonValue.JsonObject(members);
    }
}
