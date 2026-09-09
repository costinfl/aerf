package org.aerf.pipeline;

import org.aerf.analysis.calibration.CalibrationProfile;
import org.aerf.analysis.calibration.LinearCalibration;
import org.aerf.analysis.calibration.WeightedDimension;
import org.aerf.analysis.invariant.examples.SpecWorkedExamples;
import org.aerf.analysis.metrics.layer.LayerPolicy;
import org.aerf.analysis.metrics.security.rules.DefaultSecurityRules;
import org.aerf.analysis.role.graph.DefaultGraphRefinementRules;
import org.aerf.analysis.role.seed.DefaultSeedRules;
import org.aerf.model.Role;
import org.aerf.report.CalibrationJson;
import org.aerf.report.GraphJson;
import org.aerf.report.InvariantJson;
import org.aerf.report.MetricsJson;
import org.aerf.report.json.JsonObjectBuilder;
import org.aerf.report.json.JsonValue;
import org.aerf.report.json.JsonWriter;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * A runnable command-line entry point for {@link Pipeline}: {@code
 * java -cp ... org.aerf.pipeline.Main <source-root> [classpath-entry...]}
 * prints one JSON report to stdout.
 *
 * <p>{@link PipelineConfig} requires every governance-sensitive input
 * (layer policy, calibration profile, invariants) explicitly — there is
 * no default for any of them anywhere in {@code aerf-analysis} — so a
 * runnable CLI has to make some concrete choice to have anything to run
 * at all. {@link #illustrativeGovernanceConfig} is that choice, built the
 * same way as every other "illustrative, not part of v0.4" catalog in
 * this codebase ({@code DefaultSeedRules}, {@code DefaultSecurityRules},
 * {@code DefaultGraphRefinementRules}): a real deployment is expected to
 * supply its own {@link PipelineConfig} via {@link Pipeline#run}
 * directly, exactly as {@code LayerPolicy}'s own documentation asks for.
 * The layering matrix below is section 4.1's typical ordering
 * (Presentation, Application, Domain, Persistence, Infrastructure), each
 * role permitted to call itself or the next one down and nothing else —
 * the same "adjacent or self" shape the tests already rely on, not a
 * derivation this codebase treats as universal (see {@code LayerPolicy}'s
 * own javadoc on why no such derivation is provided as a default).
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("usage: Main <source-root> [classpath-entry...]");
            System.exit(2);
            return;
        }

        Path sourceRoot = Path.of(args[0]);
        List<Path> classpath = List.of(args).subList(1, args.length).stream().map(Path::of).toList();

        PipelineConfig config = illustrativeGovernanceConfig(List.of(sourceRoot), classpath);
        PipelineReport report = Pipeline.run(config);

        System.out.println(JsonWriter.write(toJson(report)));
    }

    static PipelineConfig illustrativeGovernanceConfig(List<Path> sourceRoots, List<Path> classpath) {
        LayerPolicy layerPolicy = LayerPolicy.of(
                Set.of(Role.PRESENTATION, Role.APPLICATION, Role.DOMAIN, Role.PERSISTENCE, Role.INFRASTRUCTURE),
                Set.of(Role.PRESENTATION, Role.APPLICATION, Role.DOMAIN, Role.PERSISTENCE, Role.INFRASTRUCTURE).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                role -> role,
                                Main::selfAndNextLayer)));

        // security weighted 0, not 0.25: no adapter in this project emits a
        // VIEW node or rendering evidence yet (section 11 defers JSP/WebFlow
        // entirely), so DefaultSecurityRules' one rule never has an
        // applicable opportunity to evaluate on any Java-only extraction -
        // its value is always undefined here, which AggregatedEntropy
        // treats as making the *whole* aggregate undefined unless the
        // dimension carrying it is explicitly weighted 0 (its own
        // documented escape hatch). This is a real, current capability
        // boundary of this pipeline, not an arbitrary tuning choice - see
        // docs/increment-16-*.md.
        CalibrationProfile calibrationProfile = CalibrationProfile.of(List.of(
                new WeightedDimension("layer", 1.0 / 3.0, new LinearCalibration()),
                new WeightedDimension("cycle", 1.0 / 3.0, new LinearCalibration()),
                new WeightedDimension("persistence", 1.0 / 3.0, new LinearCalibration()),
                new WeightedDimension("security", 0.0, new LinearCalibration())));

        return new PipelineConfig(
                sourceRoots,
                classpath,
                DefaultSeedRules.illustrativeRules(),
                DefaultGraphRefinementRules.illustrativeRules(),
                layerPolicy,
                false,
                DefaultSecurityRules.illustrativeRules(),
                calibrationProfile,
                List.of(SpecWorkedExamples.noPresentationToPersistence(), SpecWorkedExamples.entropyBudget(0.35)));
    }

    private static final List<Role> LAYER_ORDER = List.of(
            Role.PRESENTATION, Role.APPLICATION, Role.DOMAIN, Role.PERSISTENCE, Role.INFRASTRUCTURE);

    private static Set<Role> selfAndNextLayer(Role role) {
        int index = LAYER_ORDER.indexOf(role);
        return index + 1 < LAYER_ORDER.size()
                ? Set.of(role, LAYER_ORDER.get(index + 1))
                : Set.of(role);
    }

    private static JsonValue toJson(PipelineReport report) {
        JsonObjectBuilder invariants = new JsonObjectBuilder();
        report.invariantResults().forEach(result -> invariants.put(result.invariantName(), InvariantJson.evaluationResult(result)));

        return new JsonObjectBuilder()
                .put("graph", GraphJson.graph(report.graph()))
                .put("roleRefinementPasses", report.roleRefinementPasses())
                .put("layerEntropy", MetricsJson.layerEntropy(report.layerEntropy()))
                .put("cycleEntropy", MetricsJson.cycleEntropy(report.cycleEntropy()))
                .put("persistenceEntropy", MetricsJson.persistenceEntropy(report.persistenceEntropy()))
                .put("securityEntropy", MetricsJson.securityEntropy(report.securityEntropy()))
                .put("totalEntropy", CalibrationJson.totalEntropy(report.totalEntropy()))
                .put("maturity", CalibrationJson.maturity(report.maturity()))
                .put("maturityLevel", report.maturityLevel().map(CalibrationJson::maturityLevel).orElse(JsonValue.JsonNull.INSTANCE))
                .put("confidence", CalibrationJson.confidence(report.confidence()))
                .put("invariants", invariants.build())
                .put("extractionDiagnostics", new JsonValue.JsonArray(
                        report.extractionDiagnostics().stream().map(d -> (JsonValue) new JsonValue.JsonString(d)).toList()))
                .build();
    }
}
