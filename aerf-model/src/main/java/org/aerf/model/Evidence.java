package org.aerf.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A single piece of source evidence backing a node's attributes or an
 * edge's relation. Evidence is what makes a graph fact auditable: it names
 * the adapter that produced the fact, describes what was observed, and
 * optionally where in the source it was observed.
 *
 * <p>Evidence always represents something that was actually observed.
 * Absence of knowledge is represented elsewhere ({@link Role#UNKNOWN},
 * {@link NodeRef.Unresolved}) rather than by an "unresolved" Evidence
 * variant, so that every Evidence instance can be trusted at face value.
 * {@link #executionContext()} is the one exception to "always a positive
 * claim": its default, {@link ExecutionContext#UNKNOWN}, means the
 * adapter that produced this evidence made no claim about iteration
 * either way, distinct from {@link ExecutionContext#SINGLE}, which is
 * itself a positive observation.
 *
 * <p>{@link #attributes()} (AERF v0.4.1 patch Amendment 5) is an open,
 * adapter-defined key/value map for structured facts an adapter observed
 * — e.g. {@code annotation=org.springframework.stereotype.Controller} —
 * as an alternative to encoding the same fact only in
 * {@link #description()} prose. Neither replaces the other: description
 * remains the human-readable account of what was observed; attributes
 * are there so a rule can match a specific fact without parsing prose.
 * Defaults to empty for evidence built via the {@code of(...)} factories.
 */
public final class Evidence {

    private final String sourceAdapter;
    private final String description;
    private final String location;
    private final ExtractionFidelity fidelity;
    private final ExecutionContext executionContext;
    private final Map<String, String> attributes;

    private Evidence(String sourceAdapter, String description, String location, ExtractionFidelity fidelity,
                      ExecutionContext executionContext, Map<String, String> attributes) {
        this.sourceAdapter = Objects.requireNonNull(sourceAdapter, "sourceAdapter");
        this.description = Objects.requireNonNull(description, "description");
        this.location = location;
        this.fidelity = Objects.requireNonNull(fidelity, "fidelity");
        this.executionContext = Objects.requireNonNull(executionContext, "executionContext");
        // LinkedHashMap preserves insertion order; Map.copyOf does not, which would
        // undermine deterministic iteration over an evidence item's attributes (the
        // same pitfall documented and fixed for Node.attributes() and Graph in
        // Increment 1).
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(attributes, "attributes")));
        if (sourceAdapter.isBlank()) {
            throw new IllegalArgumentException("sourceAdapter must not be blank");
        }
        if (description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
    }

    public static Evidence of(String sourceAdapter, String description, String location, ExtractionFidelity fidelity) {
        return new Evidence(sourceAdapter, description, location, fidelity, ExecutionContext.UNKNOWN, Map.of());
    }

    public static Evidence of(String sourceAdapter, String description, ExtractionFidelity fidelity) {
        return new Evidence(sourceAdapter, description, null, fidelity, ExecutionContext.UNKNOWN, Map.of());
    }

    public static Evidence of(String sourceAdapter, String description, String location, ExtractionFidelity fidelity,
                               ExecutionContext executionContext) {
        return new Evidence(sourceAdapter, description, location, fidelity, executionContext, Map.of());
    }

    public static Evidence of(String sourceAdapter, String description, ExtractionFidelity fidelity,
                               ExecutionContext executionContext) {
        return new Evidence(sourceAdapter, description, null, fidelity, executionContext, Map.of());
    }

    /**
     * Starts building an {@code Evidence} with structured attributes.
     * Preferred over the {@code of(...)} factories when an adapter has a
     * structured fact to record (an annotation's fully-qualified name, a
     * resolved method signature, ...), rather than only prose.
     */
    public static Builder builder(String sourceAdapter, String description, ExtractionFidelity fidelity) {
        return new Builder(sourceAdapter, description, fidelity);
    }

    public String sourceAdapter() {
        return sourceAdapter;
    }

    public String description() {
        return description;
    }

    public Optional<String> location() {
        return Optional.ofNullable(location);
    }

    public ExtractionFidelity fidelity() {
        return fidelity;
    }

    public ExecutionContext executionContext() {
        return executionContext;
    }

    public Map<String, String> attributes() {
        return attributes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Evidence other)) {
            return false;
        }
        return sourceAdapter.equals(other.sourceAdapter)
                && description.equals(other.description)
                && Objects.equals(location, other.location)
                && fidelity == other.fidelity
                && executionContext == other.executionContext
                && attributes.equals(other.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceAdapter, description, location, fidelity, executionContext, attributes);
    }

    @Override
    public String toString() {
        return "Evidence{adapter=" + sourceAdapter + ", fidelity=" + fidelity
                + ", executionContext=" + executionContext
                + ", location=" + location + ", description=" + description
                + ", attributes=" + attributes + "}";
    }

    /**
     * Builds an {@link Evidence} with structured attributes attached.
     * Introduced (rather than yet more {@code of(...)} overloads) because
     * the existing four factories already cover every combination of
     * {@code location} and {@code executionContext}; a fifth optional
     * field would otherwise double that to eight.
     */
    public static final class Builder {

        private final String sourceAdapter;
        private final String description;
        private final ExtractionFidelity fidelity;
        private final Map<String, String> attributes = new LinkedHashMap<>();
        private String location;
        private ExecutionContext executionContext = ExecutionContext.UNKNOWN;

        private Builder(String sourceAdapter, String description, ExtractionFidelity fidelity) {
            this.sourceAdapter = sourceAdapter;
            this.description = description;
            this.fidelity = fidelity;
        }

        public Builder location(String location) {
            this.location = location;
            return this;
        }

        public Builder executionContext(ExecutionContext executionContext) {
            this.executionContext = Objects.requireNonNull(executionContext, "executionContext");
            return this;
        }

        public Builder attribute(String key, String value) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            if (key.isBlank()) {
                throw new IllegalArgumentException("attribute key must not be blank");
            }
            attributes.put(key, value);
            return this;
        }

        public Builder attributes(Map<String, String> attributes) {
            Objects.requireNonNull(attributes, "attributes").forEach(this::attribute);
            return this;
        }

        public Evidence build() {
            return new Evidence(sourceAdapter, description, location, fidelity, executionContext, attributes);
        }
    }
}
