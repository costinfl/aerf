package org.aerf.extraction;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * What to extract from, and with how much type information. Per the
 * ExtractionAdapter Plan's classpath decision: {@code classpath} is
 * optional. Non-empty means an extractor should resolve types against it
 * and record {@link org.aerf.model.ExtractionFidelity#L2_SYMBOL_RESOLVED}
 * evidence; empty means it must still extract — recording
 * {@link org.aerf.model.ExtractionFidelity#L1_SYNTAX} and leaving
 * unresolvable references as {@link org.aerf.model.NodeRef.Unresolved}
 * rather than guessing (AERF v0.4 section 8.1: L2 "tolerat[es] incomplete
 * classpaths," it does not require a complete one).
 */
public record ExtractionRequest(List<Path> sourceRoots, List<Path> classpath) {

    public ExtractionRequest {
        sourceRoots = List.copyOf(Objects.requireNonNull(sourceRoots, "sourceRoots"));
        classpath = List.copyOf(Objects.requireNonNull(classpath, "classpath"));
        if (sourceRoots.isEmpty()) {
            throw new IllegalArgumentException("sourceRoots must not be empty");
        }
    }

    public static ExtractionRequest of(List<Path> sourceRoots) {
        return new ExtractionRequest(sourceRoots, List.of());
    }

    public static ExtractionRequest of(List<Path> sourceRoots, List<Path> classpath) {
        return new ExtractionRequest(sourceRoots, classpath);
    }

    public boolean hasClasspath() {
        return !classpath.isEmpty();
    }
}
