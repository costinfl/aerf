package org.aerf.pipeline;

import org.aerf.analysis.calibration.CalibrationProfile;
import org.aerf.analysis.invariant.Invariant;
import org.aerf.analysis.metrics.layer.LayerPolicy;
import org.aerf.analysis.metrics.security.SecurityOpportunityRule;
import org.aerf.analysis.role.GraphRoleRefinementRule;
import org.aerf.analysis.role.RoleInferenceRule;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Everything one {@link Pipeline#run(PipelineConfig)} call needs, beyond
 * the graph itself. Every governance-sensitive input — {@link
 * #layerPolicy()}, {@link #calibrationProfile()}, {@link #invariants()} —
 * is a required, explicit field with no built-in default: {@code
 * LayerPolicy} and {@code CalibrationFunction} are both already
 * documented as "must be an explicit governance choice," and this
 * config's own job is to carry that choice through to {@link Pipeline},
 * never to make it. {@link #seedRules()}, {@link #refinementRules()}, and
 * {@link #securityRules()} accept the project's own "illustrative, not
 * part of v0.4" catalogs the same way every existing test already does
 * (e.g. {@code DefaultSeedRules.illustrativeRules()}) — they are not
 * exempt from being explicit, only from needing a bespoke example
 * per caller, since a reusable illustrative catalog already exists for
 * each.
 */
public record PipelineConfig(
        List<Path> sourceRoots,
        List<Path> classpath,
        List<RoleInferenceRule> seedRules,
        List<GraphRoleRefinementRule> refinementRules,
        LayerPolicy layerPolicy,
        boolean includeSelfCyclesInCycleEntropy,
        List<SecurityOpportunityRule> securityRules,
        CalibrationProfile calibrationProfile,
        List<Invariant> invariants) {

    public PipelineConfig {
        sourceRoots = List.copyOf(Objects.requireNonNull(sourceRoots, "sourceRoots"));
        classpath = List.copyOf(Objects.requireNonNull(classpath, "classpath"));
        seedRules = List.copyOf(Objects.requireNonNull(seedRules, "seedRules"));
        refinementRules = List.copyOf(Objects.requireNonNull(refinementRules, "refinementRules"));
        Objects.requireNonNull(layerPolicy, "layerPolicy");
        securityRules = List.copyOf(Objects.requireNonNull(securityRules, "securityRules"));
        Objects.requireNonNull(calibrationProfile, "calibrationProfile");
        invariants = List.copyOf(Objects.requireNonNull(invariants, "invariants"));
        if (sourceRoots.isEmpty()) {
            throw new IllegalArgumentException("sourceRoots must not be empty");
        }
    }
}
