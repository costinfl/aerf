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

        ExtractionResult extraction = new JavaSourceExtractor().extract(config.extraction());
        Graph extractedGraph = new GraphAssembler().assemble(extraction);

        IterativeRoleInferenceEngine roleEngine = new IterativeRoleInferenceEngine(
                new SeedRoleInferenceEngine(config.detection().seedRules()), config.detection().refinementRules());
        IterativeRoleInferenceResult roleResult = roleEngine.infer(extractedGraph);
        Graph graph = applyRoles(extractedGraph, roleResult);

        // The calculators are held in locals rather than used inline
        // because each is now asked two questions, not one: its entropy
        // value and, since OQ-13, how much of its own relevant evidence
        // resolved. Only the calculator knows its own scope - that is why
        // per-dimension confidence lives here rather than being re-derived
        // by a caller that would have to guess at it.
        LayerEntropyCalculator layerCalculator =
                LayerEntropyCalculator.withCallAndDependsRelations(config.governance().layerPolicy());
        CycleEntropyCalculator cycleCalculator =
                CycleEntropyCalculator.withCallAndDependsRelations(
                        config.governance().includeSelfCyclesInCycleEntropy());
        PersistenceEntropyCalculator persistenceCalculator = PersistenceEntropyCalculator.withCallRelation();
        SecurityEntropyCalculator securityCalculator =
                new SecurityEntropyCalculator(config.detection().securityRules());

        LayerEntropyResult layerEntropy = layerCalculator.compute(graph);
        CycleEntropyResult cycleEntropy = cycleCalculator.compute(graph);
        PersistenceEntropyResult persistenceEntropy = persistenceCalculator.compute(graph);
        SecurityEntropyResult securityEntropy = securityCalculator.compute(graph);

        Map<String, OptionalDouble> confidenceByDimension = new java.util.LinkedHashMap<>();
        confidenceByDimension.put("layer", layerCalculator.confidence(graph));
        confidenceByDimension.put("cycle", cycleCalculator.confidence(graph));
        confidenceByDimension.put("persistence", persistenceCalculator.confidence(graph));
        confidenceByDimension.put("security", securityCalculator.confidence(graph));

        Map<String, OptionalDouble> dimensionValues = Map.of(
                "layer", layerEntropy.value(),
                "cycle", cycleEntropy.value(),
                "persistence", persistenceEntropy.value(),
                "security", securityEntropy.value());
        OptionalDouble totalEntropy =
                AggregatedEntropy.compute(config.governance().calibrationProfile(), dimensionValues);
        OptionalDouble maturity = Maturity.compute(totalEntropy);
        Optional<MaturityLevel> maturityLevel = maturity.isPresent()
                ? Optional.of(MaturityLevel.classify(maturity.getAsDouble()))
                : Optional.empty();
        OptionalDouble confidence = AnalysisConfidence.compute(graph);

        InvariantEvaluator invariantEvaluator = new InvariantEvaluator();
        Map<String, Double> metricsForInvariants = graphScopeMetrics(totalEntropy);
        List<InvariantEvaluationResult> invariantResults = new ArrayList<>();
        List<String> skippedInvariants = new ArrayList<>();
        for (Invariant invariant : config.governance().invariants()) {
            if (metricsForInvariants.keySet().containsAll(invariant.referencedMetricNames())) {
                invariantResults.add(invariantEvaluator.evaluate(invariant, graph, metricsForInvariants));
            } else {
                // InvariantEvaluator.evaluate deliberately throws rather
                // than silently passing or failing when a GRAPH-scope
                // invariant references a metric that was not supplied
                // (its own test, aMissingMetricThrowsRatherThanSilentlyPassingOrFailing,
                // makes this an intentional caller-responsibility contract,
                // not an oversight) - an undefined entropy dimension is a
                // real, legitimate outcome (section 3.5/5.1), so this is
                // the caller honoring that contract: skip rather than
                // crash, but report the skip rather than let it vanish
                // silently (section 5.4's own principle, applied to
                // invariant evaluability rather than edge resolution).
                skippedInvariants.add(invariant.name());
            }
        }

        return new PipelineReport(config.governance(), graph, roleResult.passes(), layerEntropy, cycleEntropy, persistenceEntropy,
                securityEntropy, totalEntropy, maturity, maturityLevel, confidence, confidenceByDimension,
                List.copyOf(invariantResults), List.copyOf(skippedInvariants), extraction.diagnostics());
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
