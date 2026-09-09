package org.aerf.pipeline;

import org.aerf.analysis.calibration.MaturityLevel;
import org.aerf.analysis.invariant.InvariantEvaluationResult;
import org.aerf.analysis.metrics.cycle.CycleEntropyResult;
import org.aerf.analysis.metrics.layer.LayerEntropyResult;
import org.aerf.analysis.metrics.persistence.PersistenceEntropyResult;
import org.aerf.analysis.metrics.security.SecurityEntropyResult;
import org.aerf.model.Graph;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Everything one {@link Pipeline#run(PipelineConfig)} call produces:
 * the role-classified graph, each MVP entropy dimension's full result
 * (not just its ratio — see each result type's own documentation for
 * why "measurement before aggregation" keeps the underlying edges/SCCs/
 * findings alongside the number), the section-5 aggregate/maturity/
 * confidence values, and every configured invariant's evaluation.
 * {@link #extractionDiagnostics()} carries anything
 * {@code JavaSourceExtractor} itself reported (e.g. an unparseable
 * file) that isn't a graph fact at all.
 */
public record PipelineReport(
        Graph graph,
        int roleRefinementPasses,
        LayerEntropyResult layerEntropy,
        CycleEntropyResult cycleEntropy,
        PersistenceEntropyResult persistenceEntropy,
        SecurityEntropyResult securityEntropy,
        OptionalDouble totalEntropy,
        OptionalDouble maturity,
        Optional<MaturityLevel> maturityLevel,
        OptionalDouble confidence,
        List<InvariantEvaluationResult> invariantResults,
        List<String> extractionDiagnostics) {

    public PipelineReport {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(layerEntropy, "layerEntropy");
        Objects.requireNonNull(cycleEntropy, "cycleEntropy");
        Objects.requireNonNull(persistenceEntropy, "persistenceEntropy");
        Objects.requireNonNull(securityEntropy, "securityEntropy");
        Objects.requireNonNull(totalEntropy, "totalEntropy");
        Objects.requireNonNull(maturity, "maturity");
        Objects.requireNonNull(maturityLevel, "maturityLevel");
        Objects.requireNonNull(confidence, "confidence");
        invariantResults = List.copyOf(Objects.requireNonNull(invariantResults, "invariantResults"));
        extractionDiagnostics = List.copyOf(Objects.requireNonNull(extractionDiagnostics, "extractionDiagnostics"));
    }
}
