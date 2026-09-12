package org.aerf.pipeline;

import org.aerf.analysis.calibration.EntropySnapshot;
import org.aerf.analysis.calibration.MaturityLevel;
import org.aerf.analysis.invariant.InvariantEvaluationResult;
import org.aerf.analysis.metrics.cycle.CycleEntropyResult;
import org.aerf.analysis.metrics.layer.LayerEntropyResult;
import org.aerf.analysis.metrics.persistence.PersistenceEntropyResult;
import org.aerf.analysis.metrics.security.SecurityEntropyResult;
import org.aerf.model.Graph;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * {@link #skippedInvariants()} names every configured invariant that was
 * not evaluated because it references a GRAPH-scope metric this run left
 * undefined (Increment 18) - reported explicitly rather than silently
 * absent, matching section 5.4's own principle that incomplete evidence
 * must stay visible rather than disappearing.
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
        List<String> skippedInvariants,
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
        skippedInvariants = List.copyOf(Objects.requireNonNull(skippedInvariants, "skippedInvariants"));
        extractionDiagnostics = List.copyOf(Objects.requireNonNull(extractionDiagnostics, "extractionDiagnostics"));
    }

    /**
     * The same four dimension names/keys {@link Pipeline#run} uses
     * internally when building the map it hands to {@code
     * AggregatedEntropy.compute} - reconstructed here from this report's
     * own four entropy results rather than carried as a fifth field, so
     * the two can never drift apart. Exposed as the v0.4 §5.3
     * remediation's entry point: a {@link org.aerf.analysis.calibration.Drift}
     * computation needs exactly this shape for both the baseline and
     * current measurement (AERF v0.4 contract reconciliation, item
     * V04-CAL-02 - see {@code docs/aerf-v0.4-reconciliation-evidence.md}).
     *
     * <p>Built as an explicitly ordered {@code LinkedHashMap}, not {@code
     * Map.of(...)}: {@code Pipeline.run}'s own internal map can safely use
     * {@code Map.of(...)} because {@code AggregatedEntropy} only ever
     * looks values up by key, never iterates it - but this method's
     * result ultimately reaches {@code DriftJson}'s serialized output via
     * {@link #toEntropySnapshot}, and {@code Map.of(...)}'s iteration
     * order is deliberately randomized per JVM invocation (by design, to
     * catch exactly this kind of order-dependence bug), which would
     * violate section 14's "identical sources produce identical output"
     * across separate runs, not just within one.
     */
    public Map<String, OptionalDouble> entropyByDimension() {
        Map<String, OptionalDouble> byDimension = new LinkedHashMap<>();
        byDimension.put("layer", layerEntropy.value());
        byDimension.put("cycle", cycleEntropy.value());
        byDimension.put("persistence", persistenceEntropy.value());
        byDimension.put("security", securityEntropy.value());
        return Collections.unmodifiableMap(byDimension);
    }

    /**
     * Packages this report's entropy values as an {@link EntropySnapshot}
     * for {@link org.aerf.analysis.calibration.Drift#compute} - the
     * missing link the v0.4/v0.4.1 contract reconciliation found: nothing
     * before this method turned a real {@code PipelineReport} into the
     * one shape {@code Drift} actually accepts, so section 5.3's formula,
     * though implemented and unit-tested, was not reachable from an
     * actual pipeline run at all.
     *
     * @param subjectId identifies what was scanned (e.g. a project id),
     *     mirroring the dashboard's own {@code project_id} column
     *     (Increment 19) - not generated or looked up here, since this
     *     report has no concept of project identity of its own and
     *     inventing one would be exactly the kind of storage/API
     *     decision {@link org.aerf.analysis.calibration.Drift}'s own
     *     javadoc explains this pipeline deliberately leaves to whatever
     *     caller already has that context.
     */
    public EntropySnapshot toEntropySnapshot(String subjectId) {
        return new EntropySnapshot(subjectId, entropyByDimension());
    }
}
