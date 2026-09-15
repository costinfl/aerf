package org.aerf.pipeline;

import org.aerf.analysis.governance.GovernancePolicy;
import org.aerf.analysis.governance.Subsystem;
import org.aerf.analysis.governance.Subsystems;
import org.aerf.analysis.metrics.cycle.CycleEntropyResult;
import org.aerf.analysis.metrics.cycle.SubsystemCycleEntropy;
import org.aerf.report.json.JsonWriter;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Increment 27 (OQ-06)'s reachability proof: scoped cycle entropy has to
 * work through a real {@link Pipeline#run} over parsed source and reach
 * the serialized report, not merely be unit-testable in the calculator —
 * the standard the V04-CAL-02 correction established.
 *
 * <p>This needs its own fixture. {@code defect-sample} is a strict DAG
 * and is never modified; before {@code cyclic-sample} there was no cyclic
 * graph anywhere in this project outside two hand-built unit-test
 * fixtures, and — the fact that made this increment's evidence position
 * awkward — no cycle in any real repository scanned so far either.
 *
 * <p>{@code cyclic-sample} carries both shapes the semantics turn on: a
 * cycle confined to one subsystem ({@code Invoice} ⇄ {@code LineItem},
 * both in billing) and one crossing a boundary ({@code Ledger} ⇄
 * {@code Crate}). It also has an acyclic node in shipping and a class in
 * neither declared subsystem.
 */
class CycleScopeEndToEndTest {

    private static final String BILLING = "com.example.billing";
    private static final String SHIPPING = "com.example.shipping";

    @Test
    void aRealPipelineRunFindsBothCycleShapesAndScopesThemToTheDeclaredSubsystems() {
        CycleEntropyResult scoped = Pipeline.run(config(Subsystems.of(List.of(
                Subsystem.of("billing", BILLING),
                Subsystem.of("shipping", SHIPPING))))).cycleEntropy();

        // Graph-wide first: 12 nodes (6 types, 6 methods), 4 of them in a
        // cycle, across two SCCs.
        assertEquals(12, scoped.totalNodeCount());
        assertEquals(2, scoped.relevantSccs().size());
        assertEquals(OptionalDouble.of(4.0 / 12.0), scoped.value());

        // billing claims 3 types and their 3 methods; all three types are
        // in a cycle - the intra-billing pair plus its half of the
        // crossing one.
        assertEquals(OptionalDouble.of(3.0 / 6.0), reading(scoped, "billing").value());
        // shipping claims Crate and Label with their methods; only Crate
        // is in a cycle, and only its half of the crossing one.
        assertEquals(OptionalDouble.of(1.0 / 4.0), reading(scoped, "shipping").value());
    }

    @Test
    void theGraphWideMeasurementIsIdenticalWhetherOrNotSubsystemsAreDeclared() {
        CycleEntropyResult unscoped = Pipeline.run(config(Subsystems.none())).cycleEntropy();
        CycleEntropyResult scoped = Pipeline.run(config(Subsystems.of(List.of(
                Subsystem.of("billing", BILLING),
                Subsystem.of("shipping", SHIPPING))))).cycleEntropy();

        assertEquals(unscoped.value(), scoped.value());
        assertEquals(unscoped.relevantSccs(), scoped.relevantSccs(),
                "SCC detection sees the same graph whatever governance declares");
        assertTrue(unscoped.bySubsystem().isEmpty());
    }

    @Test
    void theCrossSubsystemCycleIsCountedByBothSidesAndOwnedByNeither() {
        CycleEntropyResult scoped = Pipeline.run(config(Subsystems.of(List.of(
                Subsystem.of("billing", BILLING),
                Subsystem.of("shipping", SHIPPING))))).cycleEntropy();

        assertTrue(reading(scoped, "billing").participatingNodes().stream()
                        .anyMatch(id -> id.value().equals(BILLING + ".Ledger")),
                "billing counts its own half");
        assertTrue(reading(scoped, "shipping").participatingNodes().stream()
                        .anyMatch(id -> id.value().equals(SHIPPING + ".Crate")),
                "shipping counts its own half");
        assertTrue(reading(scoped, "billing").participatingNodes().stream()
                        .noneMatch(id -> id.value().startsWith(SHIPPING)),
                "and neither reaches across the boundary to claim the other's node");
    }

    @Test
    void anUnclaimedClassCountsGraphWideAndInNoScopedReading() {
        CycleEntropyResult scoped = Pipeline.run(config(Subsystems.of(List.of(
                Subsystem.of("billing", BILLING),
                Subsystem.of("shipping", SHIPPING))))).cycleEntropy();

        int claimed = scoped.bySubsystem().stream().mapToInt(SubsystemCycleEntropy::totalNodeCount).sum();

        assertEquals(10, claimed);
        assertEquals(12, scoped.totalNodeCount(),
                "com.example.shared.Clock and its method are in neither subsystem");
    }

    @Test
    void aSubsystemClaimingNothingInThisGraphReadsUndefinedThroughTheWholePipeline() {
        CycleEntropyResult scoped = Pipeline.run(config(Subsystems.of(List.of(
                Subsystem.of("warehouse", "com.example.warehouse"))))).cycleEntropy();

        assertTrue(reading(scoped, "warehouse").value().isEmpty(),
                "undefined, not 0.0 - the subsystem was not measurable here at all");
    }

    @Test
    void bothTheGlobalAndTheScopedMeasurementsReachTheSerializedReport() {
        PipelineReport report = Pipeline.run(config(Subsystems.of(List.of(
                Subsystem.of("billing", BILLING),
                Subsystem.of("shipping", SHIPPING)))));

        String json = JsonWriter.write(Main.toJson(report));

        assertTrue(json.contains("\"subsystem\":\"billing\",\"value\":0.5"), json);
        assertTrue(json.contains("\"subsystem\":\"shipping\",\"value\":0.25"), json);
        assertTrue(json.contains("\"value\":0.3333333333333333"), json);
        assertTrue(json.contains("\"name\":\"billing\",\"idPrefix\":\"com.example.billing\""), json);
    }

    private static SubsystemCycleEntropy reading(CycleEntropyResult result, String subsystem) {
        return result.bySubsystem().stream()
                .filter(s -> s.subsystem().equals(subsystem))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no scoped reading for " + subsystem));
    }

    private static PipelineConfig config(Subsystems subsystems) {
        PipelineConfig base = Main.illustrativeConfig(List.of(cyclicSampleRoot()), List.of());
        GovernancePolicy g = base.governance();
        return new PipelineConfig(base.extraction(), base.detection(), new GovernancePolicy(
                g.layerPolicy(), subsystems, g.includeSelfCyclesInCycleEntropy(),
                g.calibrationProfile(), g.invariants(), g.invariantWeights(), g.approvedExceptions()));
    }

    private static Path cyclicSampleRoot() {
        try {
            URL url = CycleScopeEndToEndTest.class.getClassLoader().getResource("cyclic-sample");
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("cyclic-sample resource not found", e);
        }
    }
}
