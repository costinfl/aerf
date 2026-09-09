package org.aerf.pipeline;

import org.aerf.analysis.calibration.AggregatedEntropy;
import org.aerf.analysis.calibration.AnalysisConfidence;
import org.aerf.analysis.calibration.Maturity;
import org.aerf.analysis.calibration.MaturityLevel;
import org.aerf.analysis.invariant.Invariant;
import org.aerf.analysis.invariant.InvariantEvaluationResult;
import org.aerf.analysis.invariant.InvariantEvaluator;
import org.aerf.analysis.metrics.cycle.CycleEntropyCalculator;
import org.aerf.analysis.metrics.cycle.CycleEntropyResult;
import org.aerf.analysis.metrics.layer.LayerEntropyCalculator;
import org.aerf.analysis.metrics.layer.LayerEntropyResult;
import org.aerf.analysis.metrics.persistence.PersistenceEntropyCalculator;
import org.aerf.analysis.metrics.persistence.PersistenceEntropyResult;
import org.aerf.analysis.metrics.security.SecurityEntropyCalculator;
import org.aerf.analysis.metrics.security.SecurityEntropyResult;
import org.aerf.analysis.role.IterativeRoleInferenceEngine;
import org.aerf.analysis.role.IterativeRoleInferenceResult;
import org.aerf.analysis.role.RoleInferenceResult;
import org.aerf.analysis.role.SeedRoleInferenceEngine;
import org.aerf.extraction.ExtractionRequest;
import org.aerf.extraction.ExtractionResult;
import org.aerf.extraction.GraphAssembler;
import org.aerf.model.Edge;
import org.aerf.model.Graph;
import org.aerf.model.Node;
import org.aerf.model.NodeId;
import org.aerf.openrewrite.JavaSourceExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The ExtractionAdapter Plan's Increment 16 entry point: composes every
 * stage built across Increments 1-15 into one run over real Java
 * source — extraction (section 7/9) -&gt; role inference (section 3.2,
 * seed plus graph-relationship refinement to a fixed point) -&gt; the
 * four MVP entropy metrics (section 4) -&gt; invariant evaluation
 * (section 6) -&gt; aggregation, maturity, and confidence (section 5).
 * JSON serialization of a {@link PipelineReport} is the caller's job
 * ({@code aerf-report}'s writers already do this per-piece; see {@link
 * Main} for a worked example), not this class's — {@code Pipeline}
 * stops at producing the report, exactly where {@code aerf-report}'s own
 * scope already begins.
 *
 * <p>Every stage here is a call to an existing, independently-tested
 * class from an earlier increment; this class adds no analysis logic of
 * its own; it exists to determine what runs in what order and to carry
 * data between stages, and to be the one place that shape is verified
 * against real, parsed source rather than only against hand-built test
 * graphs (see {@code docs/increment-16-*.md}'s "Evidence" section).
 */
public final class Pipeline {

    private Pipeline() {
    }

    public static PipelineReport run(PipelineConfig config) {
        Objects.requireNonNull(config, "config");

        ExtractionResult extraction = new JavaSourceExtractor()
                .extract(new ExtractionRequest(config.sourceRoots(), config.classpath()));
        Graph extractedGraph = new GraphAssembler().assemble(extraction);

        IterativeRoleInferenceEngine roleEngine = new IterativeRoleInferenceEngine(
                new SeedRoleInferenceEngine(config.seedRules()), config.refinementRules());
        IterativeRoleInferenceResult roleResult = roleEngine.infer(extractedGraph);
        Graph graph = applyRoles(extractedGraph, roleResult);

        LayerEntropyResult layerEntropy = LayerEntropyCalculator.withCallAndDependsRelations(config.layerPolicy())
                .compute(graph);
        CycleEntropyResult cycleEntropy = CycleEntropyCalculator.withCallAndDependsRelations(
                config.includeSelfCyclesInCycleEntropy()).compute(graph);
        PersistenceEntropyResult persistenceEntropy = PersistenceEntropyCalculator.withCallRelation().compute(graph);
        SecurityEntropyResult securityEntropy = new SecurityEntropyCalculator(config.securityRules()).compute(graph);

        Map<String, OptionalDouble> dimensionValues = Map.of(
                "layer", layerEntropy.value(),
                "cycle", cycleEntropy.value(),
                "persistence", persistenceEntropy.value(),
                "security", securityEntropy.value());
        OptionalDouble totalEntropy = AggregatedEntropy.compute(config.calibrationProfile(), dimensionValues);
        OptionalDouble maturity = Maturity.compute(totalEntropy);
        Optional<MaturityLevel> maturityLevel = maturity.isPresent()
                ? Optional.of(MaturityLevel.classify(maturity.getAsDouble()))
                : Optional.empty();
        OptionalDouble confidence = AnalysisConfidence.compute(graph);

        InvariantEvaluator invariantEvaluator = new InvariantEvaluator();
        Map<String, Double> metricsForInvariants = graphScopeMetrics(totalEntropy);
        List<InvariantEvaluationResult> invariantResults = new ArrayList<>();
        for (Invariant invariant : config.invariants()) {
            invariantResults.add(invariantEvaluator.evaluate(invariant, graph, metricsForInvariants));
        }

        return new PipelineReport(graph, roleResult.passes(), layerEntropy, cycleEntropy, persistenceEntropy,
                securityEntropy, totalEntropy, maturity, maturityLevel, confidence,
                List.copyOf(invariantResults), extraction.diagnostics());
    }

    /**
     * {@code IterativeRoleInferenceEngine} (unlike {@code
     * SeedRoleInferenceEngine.inferAndApply}) returns only a role map, not
     * a new {@code Graph} — reconstructing one here mirrors exactly what
     * {@code inferAndApply} already does for the seed-only case, so both
     * engines end up producing the same shape of result for a caller.
     */
    private static Graph applyRoles(Graph graph, IterativeRoleInferenceResult roleResult) {
        Map<NodeId, RoleInferenceResult> results = roleResult.results();
        Graph.Builder builder = Graph.builder();
        for (Node node : graph.nodes()) {
            builder.addNode(node.withRole(results.get(node.id()).role()));
        }
        for (Edge edge : graph.edges()) {
            builder.addEdge(edge);
        }
        return builder.build();
    }

    /**
     * The only GRAPH-scope metric name any invariant in this codebase
     * references is {@code total_entropy} ({@code entropy_budget},
     * section 6.4) — supplied only when defined, since {@code
     * InvariantEvaluator} throws for a GRAPH-scope invariant naming a
     * metric that wasn't provided, and an undefined total entropy
     * (section 5.1) is a real, distinct outcome an invariant referencing
     * it must be free to not apply to, not a reason to fabricate a
     * stand-in value.
     */
    private static Map<String, Double> graphScopeMetrics(OptionalDouble totalEntropy) {
        Map<String, Double> metrics = new java.util.LinkedHashMap<>();
        totalEntropy.ifPresent(v -> metrics.put("total_entropy", v));
        return metrics;
    }
}
