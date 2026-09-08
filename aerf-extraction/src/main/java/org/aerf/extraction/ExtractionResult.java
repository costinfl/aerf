package org.aerf.extraction;

import java.util.List;
import java.util.Objects;

/**
 * Every fact one {@link SourceExtractor} run produced, plus free-text
 * diagnostics (e.g. "file X could not be parsed") an adapter wants
 * surfaced but that don't fit the {@link NodeFact}/{@link EdgeFact}
 * evidence model. Facts only — never a partially-built {@code Graph}:
 * assembling facts into a graph is {@link GraphAssembler}'s job, kept
 * separate so an extractor never has to know about node/edge ordering
 * constraints.
 */
public record ExtractionResult(List<NodeFact> nodes, List<EdgeFact> edges, List<String> diagnostics) {

    public ExtractionResult {
        nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        edges = List.copyOf(Objects.requireNonNull(edges, "edges"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    public static ExtractionResult of(List<NodeFact> nodes, List<EdgeFact> edges) {
        return new ExtractionResult(nodes, edges, List.of());
    }
}
