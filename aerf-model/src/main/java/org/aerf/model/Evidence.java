package org.aerf.model;

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
 */
public final class Evidence {

    private final String sourceAdapter;
    private final String description;
    private final String location;
    private final ExtractionFidelity fidelity;
    private final ExecutionContext executionContext;

    private Evidence(String sourceAdapter, String description, String location, ExtractionFidelity fidelity,
                      ExecutionContext executionContext) {
        this.sourceAdapter = Objects.requireNonNull(sourceAdapter, "sourceAdapter");
        this.description = Objects.requireNonNull(description, "description");
        this.location = location;
        this.fidelity = Objects.requireNonNull(fidelity, "fidelity");
        this.executionContext = Objects.requireNonNull(executionContext, "executionContext");
        if (sourceAdapter.isBlank()) {
            throw new IllegalArgumentException("sourceAdapter must not be blank");
        }
        if (description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
    }

    public static Evidence of(String sourceAdapter, String description, String location, ExtractionFidelity fidelity) {
        return new Evidence(sourceAdapter, description, location, fidelity, ExecutionContext.UNKNOWN);
    }

    public static Evidence of(String sourceAdapter, String description, ExtractionFidelity fidelity) {
        return new Evidence(sourceAdapter, description, null, fidelity, ExecutionContext.UNKNOWN);
    }

    public static Evidence of(String sourceAdapter, String description, String location, ExtractionFidelity fidelity,
                               ExecutionContext executionContext) {
        return new Evidence(sourceAdapter, description, location, fidelity, executionContext);
    }

    public static Evidence of(String sourceAdapter, String description, ExtractionFidelity fidelity,
                               ExecutionContext executionContext) {
        return new Evidence(sourceAdapter, description, null, fidelity, executionContext);
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
                && executionContext == other.executionContext;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceAdapter, description, location, fidelity, executionContext);
    }

    @Override
    public String toString() {
        return "Evidence{adapter=" + sourceAdapter + ", fidelity=" + fidelity
                + ", executionContext=" + executionContext
                + ", location=" + location + ", description=" + description + "}";
    }
}
