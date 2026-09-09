package org.aerf.pipeline;

import org.aerf.analysis.calibration.CalibrationProfile;
import org.aerf.analysis.calibration.LinearCalibration;
import org.aerf.analysis.calibration.WeightedDimension;
import org.aerf.analysis.invariant.InvariantEvaluationResult;
import org.aerf.analysis.invariant.examples.SpecWorkedExamples;
import org.aerf.analysis.metrics.layer.LayerPolicy;
import org.aerf.analysis.metrics.security.rules.DefaultSecurityRules;
import org.aerf.analysis.role.graph.DefaultGraphRefinementRules;
import org.aerf.analysis.role.seed.DefaultSeedRules;
import org.aerf.extraction.JavaNodeIds;
import org.aerf.model.Node;
import org.aerf.model.NodeId;
import org.aerf.model.Role;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ExtractionAdapter Plan's own Verification section, made executable:
 * runs {@link Pipeline} over {@code src/test/resources/defect-sample} —
 * real, parsed Java carrying three deliberately planted defects — and
 * confirms each is actually found, exactly as the plan asks Increment 16
 * to prove: "a direct Presentation→Persistence layering violation, an
 * N+1 loop, and an inheritance chain requiring multi-pass role
 * refinement."
 */
class PipelineTest {

    private static Path defectSampleRoot() {
        try {
            URL url = PipelineTest.class.getClassLoader().getResource("defect-sample");
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("defect-sample resource not found", e);
        }
    }

    private static PipelineConfig config() {
        LayerPolicy layerPolicy = LayerPolicy.of(
                Set.of(Role.PRESENTATION, Role.APPLICATION, Role.PERSISTENCE),
                java.util.Map.of(
                        Role.PRESENTATION, Set.of(Role.PRESENTATION, Role.APPLICATION),
                        Role.APPLICATION, Set.of(Role.APPLICATION, Role.PERSISTENCE),
                        Role.PERSISTENCE, Set.of(Role.PERSISTENCE)));

        // security weighted 0: see Main's identical choice and the
        // increment doc for why - this sample (like every real analysis
        // this pipeline can currently run) has no VIEW/rendering evidence
        // source at all, so the security dimension is always undefined,
        // and AggregatedEntropy requires an undefined nonzero-weight
        // dimension to make the whole aggregate undefined.
        CalibrationProfile calibrationProfile = CalibrationProfile.of(List.of(
                new WeightedDimension("layer", 1.0 / 3.0, new LinearCalibration()),
                new WeightedDimension("cycle", 1.0 / 3.0, new LinearCalibration()),
                new WeightedDimension("persistence", 1.0 / 3.0, new LinearCalibration()),
                new WeightedDimension("security", 0.0, new LinearCalibration())));

        return new PipelineConfig(
                List.of(defectSampleRoot()),
                List.of(),
                DefaultSeedRules.illustrativeRules(),
                DefaultGraphRefinementRules.illustrativeRules(),
                layerPolicy,
                false,
                DefaultSecurityRules.illustrativeRules(),
                calibrationProfile,
                List.of(SpecWorkedExamples.noPresentationToPersistence(), SpecWorkedExamples.entropyBudget(0.35)));
    }

    @Test
    void aGraphScopeInvariantIsSkippedRatherThanCrashingWhenItsMetricIsUndefined() {
        // Increment 18, found by running the pipeline against a real
        // cloned repository (spring-petclinic): a real Spring Data
        // repository idiomatically has no explicit @Repository annotation
        // at all (Spring Data recognizes it by its Repository/JpaRepository
        // supertype instead), so persistenceEntropy can legitimately come
        // back undefined on real code even though it never does on this
        // sample. Reproduced here by weighting security nonzero: this
        // sample (like petclinic) has no VIEW node, so securityEntropy is
        // always undefined, which - unweighted at 0 as config() does -
        // AggregatedEntropy would otherwise treat as making total_entropy
        // undefined too, and InvariantEvaluator.evaluate deliberately
        // throws for a GRAPH-scope invariant whose metric is missing.
        // Pipeline must not crash: it must skip entropy_budget and report
        // the skip, not let it vanish or blow up the whole run.
        CalibrationProfile calibrationProfileWithSecurityWeighted = CalibrationProfile.of(List.of(
                new WeightedDimension("layer", 0.25, new LinearCalibration()),
                new WeightedDimension("cycle", 0.25, new LinearCalibration()),
                new WeightedDimension("persistence", 0.25, new LinearCalibration()),
                new WeightedDimension("security", 0.25, new LinearCalibration())));
        PipelineConfig baseConfig = config();
        PipelineConfig config = new PipelineConfig(
                baseConfig.sourceRoots(), baseConfig.classpath(), baseConfig.seedRules(),
                baseConfig.refinementRules(), baseConfig.layerPolicy(), baseConfig.includeSelfCyclesInCycleEntropy(),
                baseConfig.securityRules(), calibrationProfileWithSecurityWeighted, baseConfig.invariants());

        PipelineReport report = Pipeline.run(config);

        assertTrue(report.totalEntropy().isEmpty());
        assertEquals(List.of("entropy_budget"), report.skippedInvariants());
        assertTrue(report.invariantResults().stream().anyMatch(r -> r.invariantName().equals("no_presentation_to_persistence")),
                "an invariant referencing no metric must still be evaluated normally");
    }

    @Test
    void findsTheDirectPresentationToPersistenceLayeringViolation() {
        PipelineReport report = Pipeline.run(config());

        assertFalse(report.layerEntropy().violatingEdges().isEmpty());
        assertEquals(OptionalDouble.of(1.0), report.layerEntropy().value(),
                "every layer-relevant edge in this sample (the field DEPENDS and both direct CALLs) "
                        + "bypasses the Application layer entirely");

        Optional<InvariantEvaluationResult> noPresentationToPersistence = report.invariantResults().stream()
                .filter(r -> r.invariantName().equals("no_presentation_to_persistence"))
                .findFirst();
        assertTrue(noPresentationToPersistence.isPresent());
        assertFalse(noPresentationToPersistence.get().holds(),
                "OrderController depends on and calls OrderRepository directly, skipping the Application layer");
    }

    @Test
    void findsTheNPlusOneLoop() {
        PipelineReport report = Pipeline.run(config());

        assertEquals(1, report.persistenceEntropy().flaggedEdges().size());
        assertEquals(OptionalDouble.of(0.5), report.persistenceEntropy().value(),
                "one of the two direct calls to OrderRepository#findById is inside a for-each loop "
                        + "(getManyOrders), the other is not (getOrder)");
    }

    @Test
    void resolvesTheThreeLevelInheritanceChainOnlyAfterTwoRefinementPasses() {
        PipelineReport report = Pipeline.run(config());

        assertEquals(2, report.roleRefinementPasses(),
                "OrderRepository is seeded directly; JpaOrderRepository can only inherit PERSISTENCE "
                        + "from it in pass 1, and OrderRepositoryImpl can only inherit from "
                        + "JpaOrderRepository in pass 2, since IterativeRoleInferenceEngine snapshots "
                        + "roles once per pass rather than seeing same-pass updates");

        NodeId repositoryImplId = JavaNodeIds.type("com.example.OrderRepositoryImpl");
        Node repositoryImpl = report.graph().node(repositoryImplId).orElseThrow();
        assertEquals(Role.PERSISTENCE, repositoryImpl.role());

        NodeId jpaRepositoryId = JavaNodeIds.type("com.example.JpaOrderRepository");
        Node jpaRepository = report.graph().node(jpaRepositoryId).orElseThrow();
        assertEquals(Role.PERSISTENCE, jpaRepository.role());
    }

    @Test
    void producesADefinedTotalEntropyAndMaturityWithConfidenceReflectingTwoJdkBoundaryReferences() {
        PipelineReport report = Pipeline.run(config());

        assertTrue(report.totalEntropy().isPresent());
        assertTrue(report.maturity().isPresent());
        assertTrue(report.maturityLevel().isPresent());

        // 5 of 7 edges have both endpoints resolved. The two unresolved
        // ones are the same extractor-vs-graph-resolution pattern
        // Increment 13 first found with java.io.Serializable, recurring
        // here for two more relation kinds: Order's own "private final
        // Long id" field resolves to java.lang.Long at L2 fidelity, and
        // getManyOrders' "orders.add(...)" call resolves to
        // java.util.List#add - but neither java.lang.Long nor
        // java.util.List was itself extracted (both are JDK types, not
        // part of this source set), so GraphAssembler correctly reports
        // both edges' targets as NodeRef.Unresolved despite the
        // extractor's own confident L2 resolution. AnalysisConfidence
        // (section 5.4) is doing exactly its job here: surfacing genuine
        // incompleteness at the boundary of what this pipeline actually
        // extracted, not a defect in the sample or the pipeline.
        assertEquals(OptionalDouble.of(5.0 / 7.0), report.confidence());
    }

    @Test
    void extractionProducesNoDiagnostics() {
        PipelineReport report = Pipeline.run(config());

        assertTrue(report.extractionDiagnostics().isEmpty());
    }
}
