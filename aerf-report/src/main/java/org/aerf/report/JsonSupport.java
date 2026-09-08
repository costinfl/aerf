package org.aerf.report;

import org.aerf.model.NodeId;
import org.aerf.report.json.JsonValue;

import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Function;

/** Small helpers shared by every domain-specific JSON mapper in this package. */
final class JsonSupport {

    private JsonSupport() {
    }

    /**
     * An undefined {@code OptionalDouble} serializes to JSON {@code null},
     * never to {@code 0.0} or an omitted field — the same undefined-vs-zero
     * distinction every entropy calculator preserves in code must remain
     * visible in the serialized report too.
     */
    static JsonValue optionalDouble(OptionalDouble value) {
        return value.isPresent() ? new JsonValue.JsonNumber(value.getAsDouble()) : JsonValue.JsonNull.INSTANCE;
    }

    static JsonValue string(NodeId id) {
        return new JsonValue.JsonString(id.value());
    }

    static JsonValue string(Enum<?> value) {
        return new JsonValue.JsonString(value.name());
    }

    static <T> JsonValue array(List<T> items, Function<T, JsonValue> mapper) {
        return new JsonValue.JsonArray(items.stream().map(mapper).toList());
    }
}
