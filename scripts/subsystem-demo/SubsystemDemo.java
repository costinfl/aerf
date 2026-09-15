package org.aerf.pipeline;

import org.aerf.analysis.governance.*;
import org.aerf.analysis.metrics.layer.LayerPolicy;
import org.aerf.model.Role;
import org.aerf.report.json.JsonWriter;
import java.nio.file.Path;
import java.util.*;

/**
 * Increment 26 real-repo demonstration: spring-petclinic with two
 * declared subsystems, `owner` governed strictly and `vet` treated as a
 * legacy era that tolerates a controller reaching a repository directly.
 */
public class SubsystemDemo {
    private static final String PKG = "org.springframework.samples.petclinic.";

    public static void main(String[] a) throws Exception {
        List<Path> roots = List.of(Path.of(a[0]));
        List<Path> cp = Arrays.stream(a).skip(1).map(Path::of).toList();

        PipelineConfig base = Main.illustrativeConfig(roots, cp);
        GovernancePolicy g = base.governance();

        LayerPolicy strict = g.layerPolicy();
        LayerPolicy legacy = LayerPolicy.of(
                strict.knownRoles(),
                permitPresentationToPersistence(strict));

        GovernancePolicy withSubsystems = new GovernancePolicy(
                g.layerPolicy(),
                Subsystems.of(List.of(
                        Subsystem.withLayerPolicy("owner", PKG + "owner", strict),
                        Subsystem.withLayerPolicy("vet", PKG + "vet", legacy))),
                g.includeSelfCyclesInCycleEntropy(), g.calibrationProfile(), g.invariants(),
                g.invariantWeights(), g.approvedExceptions());

        PipelineReport r = Pipeline.run(
                new PipelineConfig(base.extraction(), base.detection(), withSubsystems));
        System.out.println(JsonWriter.write(Main.toJson(r)));
    }

    private static Map<Role, Set<Role>> permitPresentationToPersistence(LayerPolicy strict) {
        Map<Role, Set<Role>> relaxed = new LinkedHashMap<>(strict.allowedTargets());
        Set<Role> fromPresentation = new LinkedHashSet<>(
                relaxed.getOrDefault(Role.PRESENTATION, Set.of()));
        fromPresentation.add(Role.PERSISTENCE);
        relaxed.put(Role.PRESENTATION, fromPresentation);
        return relaxed;
    }
}
